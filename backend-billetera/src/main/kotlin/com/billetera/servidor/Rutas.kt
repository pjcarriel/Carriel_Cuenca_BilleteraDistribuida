// =====================================================================
//   Es el "MENÚ" del servidor: las direcciones que la app puede llamar
//   (/auth/login, /depositar, /transferir...) y qué se hace en cada una.
// =====================================================================

package com.billetera.servidor

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.IOException

private val log = LoggerFactory.getLogger("Rutas")

// Revisa QUIÉN hace la petición: toma el token (de la cabecera o de ?token=) y valida la sesión.
private suspend fun ApplicationCall.sesion(): SesionUsuario {
    val token = request.headers["Authorization"]?.removePrefix("Bearer ")?.trim()?.takeIf { it.isNotEmpty() }
        ?: request.queryParameters["token"]
        ?: throw noAutenticado()
    return UsuarioRepositorio.validarSesion(token)
}

// Igual que sesion(), pero solo deja pasar al laboratorio (si no, error 403).
private suspend fun ApplicationCall.sesionLaboratorio(): SesionUsuario =
    sesion().also { if (it.cuenta != CUENTA_LABORATORIO) throw sinPermiso() }

// Registra todas las rutas. GET = pedir datos; POST = hacer una acción.
fun Application.configurarRutas() {
    routing {

        // Para comprobar que el servidor está encendido.
        get("/") { call.respondText("Servidor Billetera Distribuida: OK") }

        // ===================== REGISTRO E INICIO DE SESIÓN =====================

        // Crea un usuario nuevo con su cuenta y $100 de regalo.
        post("/auth/registro") {
            call.respond(HttpStatusCode.Created, UsuarioRepositorio.registrar(call.receive<RegistroRequest>()))
        }

        // Inicia sesión y devuelve el token.
        post("/auth/login") {
            call.respond(UsuarioRepositorio.login(call.receive<LoginRequest>()))
        }

        // Cierra la sesión.
        post("/auth/logout") {
            val token = call.request.headers["Authorization"]?.removePrefix("Bearer ")?.trim() ?: throw noAutenticado()
            UsuarioRepositorio.logout(token)
            call.respond(HttpStatusCode.NoContent)
        }

        // ===================== DINERO (necesitan sesión) =====================

        // Número, dueño y saldo de la cuenta del usuario.
        get("/mi-cuenta") {
            call.respond(CuentaRepositorio.miCuenta(call.sesion().cuenta))
        }

        // Historial de movimientos de la cuenta.
        get("/movimientos") {
            call.respond(CuentaRepositorio.movimientos(call.sesion().cuenta))
        }

        // Nombre del dueño de una cuenta, para confirmar a quién se transfiere.
        get("/cuentas/{numero}") {
            call.sesion()
            val numero = call.parameters["numero"] ?: throw datoInvalido("Falta el número de cuenta")
            call.respond(CuentaRepositorio.destinatario(numero))
        }

        // Depósito: suma dinero a la cuenta.
        post("/depositar") {
            val sesion = call.sesion()
            call.respond(CuentaRepositorio.depositar(sesion.cuenta, call.receive<MontoRequest>().monto))
        }

        // Retiro: resta dinero (error 409 si no alcanza).
        post("/retirar") {
            val sesion = call.sesion()
            call.respond(CuentaRepositorio.retirar(sesion.cuenta, call.receive<MontoRequest>().monto))
        }

        // Transferencia: mueve el dinero y avisa al instante al que recibe.
        post("/transferir") {
            val sesion = call.sesion()
            val req = call.receive<TransferenciaRequest>()
            val resultado = CuentaRepositorio.transferir(sesion.cuenta, req.cuentaDestino.trim(), req.monto)
            // El aviso va después de guardar, así solo se avisa de dinero que ya llegó.
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

        // Datos de una factura antes de pagarla.
        get("/contratos/{codigo}") {
            call.sesion()
            val codigo = call.parameters["codigo"] ?: throw datoInvalido("Falta el código de contrato")
            call.respond(CuentaRepositorio.consultarContrato(codigo, call.request.queryParameters["servicio"]))
        }

        // Paga una factura y avisa al laboratorio para que la vea como "Pagada".
        post("/pagar-servicio") {
            val sesion = call.sesion()
            val req = call.receive<PagoServicioRequest>()
            val respuesta = CuentaRepositorio.pagarServicio(sesion.cuenta, req.servicio, req.contrato)
            NotificadorWebSocket.avisarLaboratorio(
                EventoAviso("FACTURAS_ACTUALIZADAS", "La cuenta ${sesion.cuenta} pagó ${req.servicio.uppercase()} ${req.contrato.uppercase()}")
            )
            call.respond(respuesta)
        }

        // ===================== SERVICIOS Y FACTURAS =====================

        // Lista de servicios (luz, agua, internet...).
        get("/servicios") {
            call.sesion()
            call.respond(ServiciosRepositorio.servicios())
        }

        // El laboratorio agrega un servicio nuevo.
        post("/admin/servicios") {
            call.sesionLaboratorio()
            call.respond(HttpStatusCode.Created, ServiciosRepositorio.crearServicio(call.receive<NuevoServicioRequest>()))
        }

        // Facturas de una persona según su cédula.
        get("/facturas") {
            call.sesion()
            val cedula = call.request.queryParameters["cedula"] ?: throw datoInvalido("Falta la cédula")
            val servicio = call.request.queryParameters["servicio"] ?: throw datoInvalido("Falta el servicio")
            call.respond(ServiciosRepositorio.porCedula(cedula, servicio))
        }

        // ===================== SOLO LABORATORIO =====================

        // Todas las facturas, con filtros opcionales.
        get("/admin/facturas") {
            call.sesionLaboratorio()
            call.respond(ServiciosRepositorio.listar(call.request.queryParameters["estado"], call.request.queryParameters["servicio"]))
        }

        // Emite una factura nueva.
        post("/admin/facturas") {
            call.sesionLaboratorio()
            val creada = ServiciosRepositorio.crear(call.receive<NuevaFacturaRequest>())
            NotificadorWebSocket.avisarLaboratorio(EventoAviso("FACTURAS_ACTUALIZADAS", "Nueva factura ${creada.servicio} ${creada.codigo}"))
            call.respond(HttpStatusCode.Created, creada)
        }

        // ===================== TIEMPO REAL =====================

        // Conexión que se queda abierta con UN celular para mandarle avisos al instante.
        webSocket("/ws") {
            // Si la sesión no es válida, se cierra la conexión.
            val sesion = try {
                call.sesion()
            } catch (e: ErrorNegocio) {
                log.warn("WebSocket rechazado: ${e.codigo}")
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, e.codigo))
                return@webSocket
            }
            val cuenta = sesion.cuenta
            NotificadorWebSocket.registrar(cuenta, this)
            try {
                // Al conectarse, se le envía su saldo actual.
                val datos = CuentaRepositorio.miCuenta(cuenta)
                send(Frame.Text(Json.encodeToString(EventoWebSocket(tipo = "CONECTADO", cuenta = cuenta, saldo = datos.saldo))))
                // Se queda escuchando hasta que el celular se desconecte.
                for (frame in incoming) {
                    if (frame is Frame.Text) log.info("Mensaje de $cuenta: ${frame.readText()}")
                }
            } catch (e: IOException) {
                // El celular dejó de responder (ej. perdió internet).
                log.info("WebSocket de la cuenta $cuenta sin respuesta al latido (${e.message}): se da por desconectado")
            } finally {
                // Pase lo que pase, se borra de la lista de conectados.
                NotificadorWebSocket.quitar(cuenta, this)
            }
        }
    }
}
