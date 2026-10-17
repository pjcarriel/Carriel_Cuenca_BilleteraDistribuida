// Paquete del servidor.
package com.billetera.servidor

import io.ktor.websocket.Frame                         // Un mensaje del WebSocket.
import io.ktor.websocket.WebSocketSession              // Una conexión WebSocket abierta.
import kotlinx.serialization.encodeToString            // Convierte un objeto en texto JSON.
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap          // Mapa seguro para usar desde VARIOS hilos a la vez.

private val log = LoggerFactory.getLogger("NotificadorWebSocket")

// Lleva la cuenta de QUÉ celulares están conectados y les envía notificaciones.
// Es el corazón del "tiempo real": cuando llega una transferencia por REST,
// el servidor busca aquí las conexiones del receptor y le "empuja" el mensaje.
object NotificadorWebSocket {

    // Mapa: número de cuenta -> conjunto de conexiones abiertas de esa cuenta
    // (una misma cuenta podría estar abierta en dos celulares).
    // ConcurrentHashMap: varias peticiones (hilos) pueden registrar/quitar/leer al mismo tiempo sin corromperlo.
    private val sesiones = ConcurrentHashMap<String, MutableSet<WebSocketSession>>()

    // Conversor JSON; encodeDefaults = incluir también los campos con valor por defecto (ej. fecha).
    private val json = Json { encodeDefaults = true }

    // Registra una conexión nueva de una cuenta.
    fun registrar(cuenta: String, sesion: WebSocketSession) {
        // computeIfAbsent: si la cuenta no tiene conjunto todavía, lo crea (también seguro entre hilos).
        sesiones.computeIfAbsent(cuenta) { ConcurrentHashMap.newKeySet() }.add(sesion)
        log.info("WebSocket CONECTADO: cuenta $cuenta (conexiones de esa cuenta: ${sesiones[cuenta]?.size})")
    }

    // Quita una conexión cuando el celular se desconecta.
    fun quitar(cuenta: String, sesion: WebSocketSession) {
        sesiones[cuenta]?.remove(sesion)        // "?." = solo si existe el conjunto (null safety).
        log.info("WebSocket DESCONECTADO: cuenta $cuenta")
    }

    // Envía un evento a TODAS las conexiones abiertas de una cuenta.
    // "suspend": enviar por la red puede tardar; la corrutina espera sin bloquear el hilo.
    suspend fun enviar(cuenta: String, evento: EventoWebSocket) {
        val conexiones = sesiones[cuenta]
        if (conexiones.isNullOrEmpty()) {
            log.info("La cuenta $cuenta no está conectada: no se envía notificación")
            return
        }
        val texto = json.encodeToString(evento)             // Objeto -> texto JSON.
        conexiones.forEach { sesion ->
            // runCatching: si una conexión está rota, se ignora el error y se sigue con las demás.
            runCatching { sesion.send(Frame.Text(texto)) }
                .onFailure { log.warn("No se pudo enviar a $cuenta: ${it.message}") }
        }
        log.info("Notificación ${evento.tipo} enviada a la cuenta $cuenta: $texto")
    }
}
