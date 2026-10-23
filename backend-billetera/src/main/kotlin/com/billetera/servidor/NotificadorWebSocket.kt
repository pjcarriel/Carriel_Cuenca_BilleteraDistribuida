// Paquete del servidor.
package com.billetera.servidor

import io.ktor.websocket.Frame                         // Un mensaje del WebSocket.
import io.ktor.websocket.WebSocketSession              // Una conexión WebSocket abierta.
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString            // Convierte un objeto en texto JSON.
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap          // Mapa seguro para usar desde VARIOS hilos a la vez.

private val log = LoggerFactory.getLogger("NotificadorWebSocket")

// Cuenta del usuario "laboratorio": es la ÚNICA que recibe la lista de nodos conectados.
const val CUENTA_LABORATORIO = "9999"

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

    // ---------- NODOS CONECTADOS (para el panel del laboratorio) ----------
    // Foto actual de quién está conectado. StateFlow se queda solo con el ÚLTIMO valor:
    // si muchos se conectan a la vez, el laboratorio recibe directo la foto más reciente
    // (nunca una vieja que llegue después de una nueva).
    private val conexiones = MutableStateFlow(EventoConexiones(total = 0, cuentas = emptyList()))

    // Ámbito propio para enviar los avisos sin frenar a quien se conecta o desconecta.
    // SupervisorJob: si un envío falla, no se cancelan los demás.
    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // "init" se ejecuta UNA vez, la primera vez que se usa el object.
    init {
        // UNA sola corrutina envía los cambios al laboratorio, en orden.
        // launch = inicia la corrutina en segundo plano. collect = se queda ESCUCHANDO el StateFlow
        // para siempre: cada vez que cambia "conexiones.value", se ejecuta el bloque con la foto nueva.
        alcance.launch {
            conexiones.collect { foto -> enviarTexto(CUENTA_LABORATORIO, json.encodeToString(foto)) }
        }
    }

    // Registra una conexión nueva de una cuenta.
    fun registrar(cuenta: String, sesion: WebSocketSession) {
        // computeIfAbsent: si la cuenta no tiene conjunto todavía, lo crea (también seguro entre hilos).
        // ConcurrentHashMap.newKeySet() = un conjunto (Set) que también es seguro entre hilos.
        sesiones.computeIfAbsent(cuenta) { ConcurrentHashMap.newKeySet() }.add(sesion)
        log.info("WebSocket CONECTADO: cuenta $cuenta (conexiones de esa cuenta: ${sesiones[cuenta]?.size})")
        actualizarConexiones()
    }

    // Quita una conexión cuando el celular se desconecta (cierre normal, o sin respuesta al ping).
    fun quitar(cuenta: String, sesion: WebSocketSession) {
        sesiones[cuenta]?.remove(sesion)        // "?." = solo si existe el conjunto (null safety).
        log.info("WebSocket DESCONECTADO: cuenta $cuenta")
        actualizarConexiones()
    }

    // Recalcula la foto de nodos conectados (dispositivos y cuentas distintas).
    private fun actualizarConexiones() {
        // filterValues: nos quedamos solo con las cuentas que aún tienen al menos una conexión.
        val activas = sesiones.filterValues { it.isNotEmpty() }
        // Asignar ".value" PUBLICA la foto nueva: la corrutina del init la recibe y la envía al laboratorio.
        conexiones.value = EventoConexiones(
            total = activas.values.sumOf { it.size },
            cuentas = activas.keys.sorted()
        )
        log.info("Nodos conectados: ${conexiones.value.total} (${conexiones.value.cuentas})")
    }

    // Envía un evento a TODAS las conexiones abiertas de una cuenta.
    // "suspend": enviar por la red puede tardar; la corrutina espera sin bloquear el hilo.
    suspend fun enviar(cuenta: String, evento: EventoWebSocket) {
        if (sesiones[cuenta].isNullOrEmpty()) {
            log.info("La cuenta $cuenta no está conectada: no se envía notificación")
            return
        }
        val texto = json.encodeToString(evento)             // Objeto -> texto JSON.
        enviarTexto(cuenta, texto)
        log.info("Notificación ${evento.tipo} enviada a la cuenta $cuenta: $texto")
    }

    // Envía un texto ya convertido a JSON a todas las conexiones de una cuenta.
    private suspend fun enviarTexto(cuenta: String, texto: String) {
        sesiones[cuenta]?.forEach { sesion ->
            // runCatching: si una conexión está rota, se ignora el error y se sigue con las demás.
            // Frame.Text = un mensaje de TEXTO del WebSocket (aquí, el JSON). send() es suspend.
            runCatching { sesion.send(Frame.Text(texto)) }
                .onFailure { log.warn("No se pudo enviar a $cuenta: ${it.message}") }
        }
    }
}
