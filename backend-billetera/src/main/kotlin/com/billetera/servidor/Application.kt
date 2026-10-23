// Paquete del servidor.
package com.billetera.servidor

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json                     // Formato JSON para ContentNegotiation.
import io.ktor.server.application.Application
import io.ktor.server.application.install                         // Instala un "plugin" en el servidor.
import io.ktor.server.engine.embeddedServer                       // Crea el servidor dentro de nuestra app.
import io.ktor.server.cio.CIO                                     // Motor de red CIO (basado en corrutinas).
import io.ktor.server.plugins.BadRequestException                 // JSON mal formado o incompleto.
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.seconds                      // Permite escribir 15.seconds.

private val log = LoggerFactory.getLogger("Servidor")

// Puerto donde escucha el servidor. Desde el emulador se llega con http://10.0.2.2:8090
const val PUERTO = 8090

// PUNTO DE ENTRADA: lo que se ejecuta con "gradlew run".
fun main() {
    BaseDeDatos.iniciar()                                // 1. Conectar a MySQL y preparar las tablas.
    log.info("Servidor iniciando en http://localhost:$PUERTO  (emulador: http://10.0.2.2:$PUERTO)")
    // 2. Crear y arrancar el servidor con el motor CIO. host "0.0.0.0" = aceptar conexiones de cualquier interfaz.
    // Usamos CIO y no Netty: en las pruebas (Windows), Netty respondía a la PC pero NO a las conexiones
    // que llegan desde el emulador (10.0.2.2); con CIO ambas funcionan. CIO es 100 % Kotlin y usa corrutinas.
    // "module = Application::modulo" = referencia a la función que configura el servidor.
    embeddedServer(CIO, port = PUERTO, host = "0.0.0.0", module = Application::modulo)
        .start(wait = true)                              // wait = true: el programa queda escuchando.
}

// Configuración del servidor: "plugins" que agregan capacidades.
fun Application.modulo() {

    // JSON automático: call.receive<T>() lee JSON y call.respond(obj) responde JSON.
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true          // JSON con saltos de línea (más legible en Postman).
            ignoreUnknownKeys = true    // Si el cliente manda campos extra, se ignoran.
        })
    }

    // WebSockets: LATIDO (heartbeat). Cada 2 s el servidor envía un "ping"; si en 3 s no llega el "pong",
    // la conexión se considera muerta y se cierra. Así detectamos en unos 3-5 s a un celular que
    // perdió internet de golpe: ese celular no alcanza a avisar que se va, así que la única forma de
    // saberlo es notar que dejó de responder. (Un ping pesa unos pocos bytes: no afecta el rendimiento.)
    install(WebSockets) {
        pingPeriod = 2.seconds
        timeout = 3.seconds
    }

    // Manejo CENTRALIZADO de errores: convierte excepciones en respuestas HTTP con JSON.
    install(StatusPages) {
        // Errores de negocio (400 monto inválido, 404 cuenta no existe, 409 saldo insuficiente).
        exception<ErrorNegocio> { call, error ->
            log.warn("Error ${error.estado.value} ${error.codigo}: ${error.message}")
            call.respond(error.estado, ErrorRespuesta(error.codigo, error.message))
        }
        // JSON mal escrito o con campos faltantes -> 400.
        exception<BadRequestException> { call, error ->
            call.respond(HttpStatusCode.BadRequest, ErrorRespuesta("JSON_INVALIDO", "Revisa el JSON enviado: ${error.message}"))
        }
        // Cualquier otro error inesperado -> 500 (el servidor no se cae).
        exception<Throwable> { call, error ->
            // Una corrutina CANCELADA no es un error (ej. un WebSocket que se cierra porque el
            // celular se desconectó): no se registra ni se responde nada.
            if (error is CancellationException) return@exception
            log.error("Error inesperado", error)
            call.respond(HttpStatusCode.InternalServerError, ErrorRespuesta("ERROR_INTERNO", error.message ?: "Error interno"))
        }
    }

    // Registrar las rutas REST y WebSocket (Rutas.kt).
    configurarRutas()
}
