// =====================================================================
//   Es el ARRANQUE del servidor: conecta la base de datos, lo enciende
//   en el puerto 8090 y le agrega JSON, avisos en tiempo real y errores.
// =====================================================================

package com.billetera.servidor

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.cio.CIO
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.seconds

// Escribe mensajes en la consola para ver qué pasa en el servidor.
private val log = LoggerFactory.getLogger("Servidor")

// Puerto del servidor. Desde el emulador se entra con http://10.0.2.2:8090
const val PUERTO = 8090

// Lo primero que se ejecuta: conecta MySQL y enciende el servidor.
fun main() {
    BaseDeDatos.iniciar()
    log.info("Servidor iniciando en http://localhost:$PUERTO  (emulador: http://10.0.2.2:$PUERTO)")
    // "0.0.0.0" = acepta conexiones de la PC y del emulador.
    embeddedServer(CIO, port = PUERTO, host = "0.0.0.0", module = Application::modulo)
        .start(wait = true)                              // Se queda escuchando.
}

// Configura el servidor: le agrega sus "piezas".
fun Application.modulo() {

    // Para leer y responder en JSON. Si llegan campos de más, se ignoran.
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        })
    }

    // Avisos en tiempo real. Cada 2 s pregunta "¿sigues ahí?"; si en 3 s no responde, lo da por desconectado.
    install(WebSockets) {
        pingPeriod = 2.seconds
        timeout = 3.seconds
    }

    // Convierte cada error en una respuesta con mensaje para la app.
    install(StatusPages) {
        // Errores de reglas (monto inválido, cuenta no existe, sin saldo).
        exception<ErrorNegocio> { call, error ->
            log.warn("Error ${error.estado.value} ${error.codigo}: ${error.message}")
            call.respond(error.estado, ErrorRespuesta(error.codigo, error.message))
        }
        // JSON mal escrito -> error 400.
        exception<BadRequestException> { call, error ->
            call.respond(HttpStatusCode.BadRequest, ErrorRespuesta("JSON_INVALIDO", "Revisa el JSON enviado: ${error.message}"))
        }
        // Cualquier otro error -> 500, pero el servidor sigue funcionando.
        exception<Throwable> { call, error ->
            // Un celular que se desconectó no es un error real: se ignora.
            if (error is CancellationException) return@exception
            log.error("Error inesperado", error)
            call.respond(HttpStatusCode.InternalServerError, ErrorRespuesta("ERROR_INTERNO", error.message ?: "Error interno"))
        }
    }

    // Registra todas las rutas (Rutas.kt).
    configurarRutas()
}
