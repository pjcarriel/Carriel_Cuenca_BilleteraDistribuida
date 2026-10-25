// Paquete del servidor.
package com.billetera.servidor

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive                  // Lee el JSON del cuerpo de la petición.
import io.ktor.server.response.respond                 // Envía la respuesta (objeto -> JSON).
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason                   // Motivo de cierre de un WebSocket.
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.IOException                             // Error de red (ej. "Ping timeout").

private val log = LoggerFactory.getLogger("Rutas")

// Obtiene el token de la petición y valida la sesión (función de EXTENSIÓN sobre ApplicationCall).
// El token puede venir en la cabecera "Authorization: Bearer <token>" (lo normal)
// o como parámetro "?token=..." (útil para probar el WebSocket en Postman).
// Si falta o no es válido, lanza 401 (lo convierte StatusPages en Application.kt).
private suspend fun ApplicationCall.sesion(): SesionUsuario {
    // Cadena con "?." y "?:": si la cabecera no existe (null) o queda vacía (takeIf da null), se prueba
    // el parámetro ?token=; si tampoco existe, "?: throw" lanza el 401.
    val token = request.headers["Authorization"]?.removePrefix("Bearer ")?.trim()?.takeIf { it.isNotEmpty() }
        ?: request.queryParameters["token"]
        ?: throw noAutenticado()
    return UsuarioRepositorio.validarSesion(token)
}

// Igual que sesion(), pero además exige que sea el LABORATORIO (la "empresa de servicios").
// Sin sesión -> 401; con sesión de otra cuenta -> 403 SIN_PERMISO.
private suspend fun ApplicationCall.sesionLaboratorio(): SesionUsuario =
    sesion().also { if (it.cuenta != CUENTA_LABORATORIO) throw sinPermiso() }

// Rutas del servidor.
// ROUTING de Ktor: cada get("/ruta") o post("/ruta") asocia un MÉTODO HTTP + una RUTA con el bloque
// que la atiende. Cada bloque corre en una CORRUTINA propia, así que muchas peticiones se atienden
// a la vez. Dentro del bloque, "call" representa la petición actual y su respuesta:
//  - call.receive<T>()  -> lee el JSON del cuerpo y lo convierte en un objeto T.
//  - call.respond(obj)  -> responde con obj convertido a JSON (código 200 si no se indica otro).
//  - call.parameters["x"] -> lee la parte variable de la ruta, ej. {numero} en "/cuentas/{numero}".
fun Application.configurarRutas() {
    routing {

        // GET /  -> comprobar que el servidor está vivo (pública).
        get("/") { call.respondText("Servidor Billetera Distribuida: OK") }

        // ===================== AUTENTICACIÓN (públicas) =====================

        // POST /auth/registro -> 201 Created con token, cuenta nueva y $100 de bienvenida.
        post("/auth/registro") {
            call.respond(HttpStatusCode.Created, UsuarioRepositorio.registrar(call.receive<RegistroRequest>()))
        }

        // POST /auth/login -> token (o 401 si las credenciales son incorrectas).
        post("/auth/login") {
            call.respond(UsuarioRepositorio.login(call.receive<LoginRequest>()))
        }

        // POST /auth/logout -> invalida el token actual.
        post("/auth/logout") {
            val token = call.request.headers["Authorization"]?.removePrefix("Bearer ")?.trim() ?: throw noAutenticado()
            UsuarioRepositorio.logout(token)
            call.respond(HttpStatusCode.NoContent)          // 204: éxito sin contenido.
        }

        // ===================== PROTEGIDAS (requieren token) =====================

        // GET /mi-cuenta -> número, titular y saldo de la cuenta de la sesión.
        get("/mi-cuenta") {
            call.respond(CuentaRepositorio.miCuenta(call.sesion().cuenta))
        }

        // GET /movimientos -> historial de la cuenta (la app lo descarga al iniciar sesión).
        get("/movimientos") {
            call.respond(CuentaRepositorio.movimientos(call.sesion().cuenta))
        }

        // GET /cuentas/1002 -> titular de una cuenta (para confirmar a quién se transfiere).
        get("/cuentas/{numero}") {
            call.sesion()                                   // Solo usuarios con sesión.
            val numero = call.parameters["numero"] ?: throw datoInvalido("Falta el número de cuenta")
            call.respond(CuentaRepositorio.destinatario(numero))
        }

        // POST /depositar  {"monto": 50.0}
        post("/depositar") {
            val sesion = call.sesion()
            call.respond(CuentaRepositorio.depositar(sesion.cuenta, call.receive<MontoRequest>().monto))
        }

        // POST /retirar  {"monto": 20.0}   -> 409 si no hay fondos.
        post("/retirar") {
            val sesion = call.sesion()
            call.respond(CuentaRepositorio.retirar(sesion.cuenta, call.receive<MontoRequest>().monto))
        }

        // POST /transferir  {"cuentaDestino": "1001", "monto": 25.0}
        // 1) Mueve el dinero.  2) Notifica al RECEPTOR por WebSocket.  3) Responde al EMISOR.
        post("/transferir") {
            val sesion = call.sesion()
            val req = call.receive<TransferenciaRequest>()
            val resultado = CuentaRepositorio.transferir(sesion.cuenta, req.cuentaDestino.trim(), req.monto)
            // Se notifica DESPUÉS del commit: el receptor solo se entera de dinero que ya está guardado.
            // Si la transferencia falló (ej. 409), la excepción salta antes y no se notifica nada.
            NotificadorWebSocket.enviar(
                req.cuentaDestino.trim(),
                EventoWebSocket(
                    tipo = "TRANSFERENCIA_RECIBIDA",
                    cuenta = req.cuentaDestino.trim(),
                    saldo = resultado.saldoDestino,
                    cuentaOrigen = sesion.cuenta,
                    monto = resultado.respuesta.monto,
                    transaccionId = resultado.respuesta.transaccionId,
                    detalle = "De ${resultado.titularOrigen} (${sesion.cuenta})"
                )
            )
            call.respond(resultado.respuesta)
        }

        // GET /contratos/445566?servicio=LUZ -> datos de la factura (monto, titular, si está pagada).
        get("/contratos/{codigo}") {
            call.sesion()
            val codigo = call.parameters["codigo"] ?: throw datoInvalido("Falta el código de contrato")
            call.respond(CuentaRepositorio.consultarContrato(codigo, call.request.queryParameters["servicio"]))
        }

        // POST /pagar-servicio  {"servicio": "LUZ", "contrato": "445566"}
        post("/pagar-servicio") {
            val sesion = call.sesion()
            val req = call.receive<PagoServicioRequest>()
            val respuesta = CuentaRepositorio.pagarServicio(sesion.cuenta, req.servicio, req.contrato)
            // Tiempo real: el panel de servicios del laboratorio se actualiza solo ("Pagada").
            NotificadorWebSocket.avisarLaboratorio(
                EventoAviso("FACTURAS_ACTUALIZADAS", "La cuenta ${sesion.cuenta} pagó ${req.servicio.uppercase()} ${req.contrato.uppercase()}")
            )
            call.respond(respuesta)
        }

        // GET /servicios -> catálogo de servicios básicos (para el diálogo de pago y el panel).
        get("/servicios") {
            call.sesion()
            call.respond(ServiciosRepositorio.servicios())
        }

        // POST /admin/servicios -> el laboratorio agrega un servicio nuevo (201 Created; 409 si ya existe).
        post("/admin/servicios") {
            call.sesionLaboratorio()
            call.respond(HttpStatusCode.Created, ServiciosRepositorio.crearServicio(call.receive<NuevoServicioRequest>()))
        }

        // GET /facturas?cedula=1712345675&servicio=LUZ -> facturas de esa cédula (pendientes primero).
        get("/facturas") {
            call.sesion()
            val cedula = call.request.queryParameters["cedula"] ?: throw datoInvalido("Falta la cédula")
            val servicio = call.request.queryParameters["servicio"] ?: throw datoInvalido("Falta el servicio")
            call.respond(ServiciosRepositorio.porCedula(cedula, servicio))
        }

        // ===================== ADMINISTRACIÓN DE SERVICIOS (solo laboratorio) =====================

        // GET /admin/facturas?estado=PENDIENTES&servicio=LUZ -> todas las facturas (con filtros opcionales).
        get("/admin/facturas") {
            call.sesionLaboratorio()
            call.respond(ServiciosRepositorio.listar(call.request.queryParameters["estado"], call.request.queryParameters["servicio"]))
        }

        // POST /admin/facturas -> crea una factura pendiente (201 Created).
        post("/admin/facturas") {
            call.sesionLaboratorio()
            val creada = ServiciosRepositorio.crear(call.receive<NuevaFacturaRequest>())
            NotificadorWebSocket.avisarLaboratorio(EventoAviso("FACTURAS_ACTUALIZADAS", "Nueva factura ${creada.servicio} ${creada.codigo}"))
            call.respond(HttpStatusCode.Created, creada)
        }

        // WebSocket  ws://localhost:8090/ws   (cabecera Authorization: Bearer <token>, o ?token=...)
        // Dentro de este bloque, "this" ES la conexión WebSocket (una sesión) de UN celular.
        // El bloque dura lo mismo que la conexión: al salir de él, Ktor la cierra.
        webSocket("/ws") {
            // Validamos la sesión. Si no es válida, cerramos con código 1008 (POLICY VIOLATION) y el motivo.
            val sesion = try {
                call.sesion()
            } catch (e: ErrorNegocio) {
                log.warn("WebSocket rechazado: ${e.codigo}")
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, e.codigo))
                return@webSocket                            // Sale del bloque webSocket (etiqueta).
            }
            val cuenta = sesion.cuenta
            NotificadorWebSocket.registrar(cuenta, this)
            try {
                // Al conectarse le enviamos su saldo actual.
                val datos = CuentaRepositorio.miCuenta(cuenta)
                send(Frame.Text(Json.encodeToString(EventoWebSocket(tipo = "CONECTADO", cuenta = cuenta, saldo = datos.saldo))))
                // Escuchamos hasta que se cierre la conexión (suspendido, sin bloquear).
                // "incoming" es un CANAL con los mensajes que manda el celular; el for termina cuando
                // la conexión se cierra. Los ping/pong del latido los maneja Ktor solo (no llegan aquí).
                for (frame in incoming) {
                    if (frame is Frame.Text) log.info("Mensaje de $cuenta: ${frame.readText()}")
                }
            } catch (e: IOException) {
                // "Ping timeout": el celular dejó de responder al latido (perdió internet de golpe).
                // No es un error del servidor: es justo como detectamos a un nodo caído.
                log.info("WebSocket de la cuenta $cuenta sin respuesta al latido (${e.message}): se da por desconectado")
            } finally {
                // "finally" se ejecuta SIEMPRE (cierre normal, error o cancelación): la conexión nunca queda registrada.
                NotificadorWebSocket.quitar(cuenta, this)
            }
        }
    }
}
