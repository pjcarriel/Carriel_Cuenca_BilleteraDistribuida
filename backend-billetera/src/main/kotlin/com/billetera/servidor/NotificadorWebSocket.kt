// =====================================================================
//   Es el "CARTERO en tiempo real": recuerda qué celulares están conectados
//   y les manda avisos al instante (ej. "te llegó una transferencia").
// =====================================================================

package com.billetera.servidor

import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

private val log = LoggerFactory.getLogger("NotificadorWebSocket")

// Cuenta del laboratorio: la única que ve la lista de celulares conectados.
const val CUENTA_LABORATORIO = "9999"

// Maneja las conexiones abiertas con los celulares y les envía los avisos.
object NotificadorWebSocket {

    // Libreta: número de cuenta -> sus celulares conectados.
    private val sesiones = ConcurrentHashMap<String, MutableSet<WebSocketSession>>()

    private val json = Json { encodeDefaults = true }

    // "Foto" de quién está conectado ahora (para el panel del laboratorio).
    private val conexiones = MutableStateFlow(EventoConexiones(total = 0, cuentas = emptyList()))

    // Espacio para enviar avisos en segundo plano.
    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        // Cada vez que cambia la foto, se la manda al laboratorio.
        alcance.launch {
            conexiones.collect { foto -> enviarTexto(CUENTA_LABORATORIO, json.encodeToString(foto)) }
        }
    }

    // Anota un celular que se acaba de conectar.
    fun registrar(cuenta: String, sesion: WebSocketSession) {
        sesiones.computeIfAbsent(cuenta) { ConcurrentHashMap.newKeySet() }.add(sesion)
        log.info("WebSocket CONECTADO: cuenta $cuenta (conexiones de esa cuenta: ${sesiones[cuenta]?.size})")
        actualizarConexiones()
    }

    // Borra un celular que se desconectó.
    fun quitar(cuenta: String, sesion: WebSocketSession) {
        sesiones[cuenta]?.remove(sesion)
        log.info("WebSocket DESCONECTADO: cuenta $cuenta")
        actualizarConexiones()
    }

    // Vuelve a contar los celulares conectados y actualiza la foto.
    private fun actualizarConexiones() {
        val activas = sesiones.filterValues { it.isNotEmpty() }
        conexiones.value = EventoConexiones(
            total = activas.values.sumOf { it.size },
            cuentas = activas.keys.sorted()
        )
        log.info("Nodos conectados: ${conexiones.value.total} (${conexiones.value.cuentas})")
    }

    // Manda un aviso a todos los celulares de una cuenta (si no está conectada, no hace nada).
    suspend fun enviar(cuenta: String, evento: EventoWebSocket) {
        if (sesiones[cuenta].isNullOrEmpty()) {
            log.info("La cuenta $cuenta no está conectada: no se envía notificación")
            return
        }
        val texto = json.encodeToString(evento)
        enviarTexto(cuenta, texto)
        log.info("Notificación ${evento.tipo} enviada a la cuenta $cuenta: $texto")
    }

    // Aviso solo para el laboratorio (ej. "alguien pagó una factura").
    suspend fun avisarLaboratorio(evento: EventoAviso) {
        enviarTexto(CUENTA_LABORATORIO, json.encodeToString(evento))
        log.info("Aviso ${evento.tipo} al laboratorio: ${evento.detalle}")
    }

    // Envía el texto a cada celular de la cuenta; si uno falla, sigue con los demás.
    private suspend fun enviarTexto(cuenta: String, texto: String) {
        sesiones[cuenta]?.forEach { sesion ->
            runCatching { sesion.send(Frame.Text(texto)) }
                .onFailure { log.warn("No se pudo enviar a $cuenta: ${it.message}") }
        }
    }
}
