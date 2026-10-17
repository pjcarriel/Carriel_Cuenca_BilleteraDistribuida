// Paquete del servidor.
package com.billetera.servidor

import io.ktor.server.application.Application          // La aplicación Ktor.
import io.ktor.server.request.receive                  // Lee el JSON del cuerpo de la petición.
import io.ktor.server.response.respond                 // Envía la respuesta (objeto -> JSON).
import io.ktor.server.response.respondText             // Envía texto plano.
import io.ktor.server.routing.get                      // Define una ruta GET.
import io.ktor.server.routing.post                     // Define una ruta POST.
import io.ktor.server.routing.routing                  // Bloque donde se declaran las rutas.
import io.ktor.server.websocket.webSocket              // Define una ruta WebSocket.
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("Rutas")

// "fun Application.configurarRutas()" = función de EXTENSIÓN sobre Application:
// se llama como configurarRutas() dentro del módulo del servidor.
fun Application.configurarRutas() {
    routing {

        // GET /  -> para comprobar en el navegador que el servidor está vivo.
        get("/") {
            call.respondText("Servidor Billetera Distribuida: OK")
        }

        // GET /cuentas/1001 -> datos y saldo de una cuenta.
        get("/cuentas/{numero}") {
            // call.parameters["numero"] puede ser null; "?: throw" lanza un error si lo es (operador Elvis).
            val numero = call.parameters["numero"] ?: throw datoInvalido("Falta el número de cuenta")
            call.respond(CuentaRepositorio.consultar(numero))
        }

        // POST /depositar  { "cuenta": "1001", "monto": 50.0 }
        post("/depositar") {
            val req = call.receive<OperacionSimpleRequest>()   // JSON -> objeto Kotlin.
            call.respond(CuentaRepositorio.depositar(req.cuenta, req.monto))
        }

        // POST /retirar  { "cuenta": "1001", "monto": 20.0 }   -> 409 si no hay fondos.
        post("/retirar") {
            val req = call.receive<OperacionSimpleRequest>()
            call.respond(CuentaRepositorio.retirar(req.cuenta, req.monto))
        }

        // POST /transferir  { "cuentaOrigen": "1002", "cuentaDestino": "1001", "monto": 25.0 }
        // 1) Mueve el dinero en MySQL.  2) Notifica al RECEPTOR por WebSocket.  3) Responde al EMISOR.
        post("/transferir") {
            val req = call.receive<TransferenciaRequest>()
            // "val (a, b) = par" = DESESTRUCTURACIÓN: separa el Pair en dos variables.
            val (respuesta, saldoDestino) = CuentaRepositorio.transferir(req.cuentaOrigen, req.cuentaDestino, req.monto)
            NotificadorWebSocket.enviar(
                req.cuentaDestino,
                EventoWebSocket(
                    tipo = "TRANSFERENCIA_RECIBIDA",
                    cuenta = req.cuentaDestino,
                    saldo = saldoDestino,
                    cuentaOrigen = req.cuentaOrigen,
                    monto = respuesta.monto,
                    transaccionId = respuesta.transaccionId
                )
            )
            call.respond(respuesta)
        }

        // POST /pagar-servicio  { "cuenta": "1001", "servicio": "LUZ", "contrato": "445566", "monto": 32.75 }
        post("/pagar-servicio") {
            val req = call.receive<PagoServicioRequest>()
            call.respond(CuentaRepositorio.pagarServicio(req.cuenta, req.servicio, req.contrato, req.monto))
        }

        // WebSocket  ws://localhost:8080/ws/1001
        // El celular abre esta conexión y la mantiene ABIERTA; el servidor le envía mensajes cuando quiera.
        webSocket("/ws/{cuenta}") {
            val cuenta = call.parameters["cuenta"] ?: return@webSocket   // Sin cuenta: cerramos.
            NotificadorWebSocket.registrar(cuenta, this)                 // "this" = esta conexión.
            try {
                // Al conectarse, le enviamos su saldo actual (así la app se sincroniza enseguida).
                val datos = CuentaRepositorio.consultar(cuenta)
                send(Frame.Text(Json.encodeToString(EventoWebSocket(tipo = "CONECTADO", cuenta = cuenta, saldo = datos.saldo))))
                // Escuchamos lo que envíe el cliente (no esperamos nada, pero así detectamos el cierre).
                // El "for" se queda esperando (suspendido, sin bloquear) hasta que la conexión se cierra.
                for (frame in incoming) {
                    if (frame is Frame.Text) log.info("Mensaje de $cuenta: ${frame.readText()}")
                }
            } catch (e: ErrorNegocio) {
                log.warn("WebSocket rechazado para $cuenta: ${e.message}")   // Ej.: la cuenta no existe.
            } finally {
                // "finally" se ejecuta SIEMPRE: al cerrar normal o por error, quitamos la conexión.
                NotificadorWebSocket.quitar(cuenta, this)
            }
        }
    }
}
