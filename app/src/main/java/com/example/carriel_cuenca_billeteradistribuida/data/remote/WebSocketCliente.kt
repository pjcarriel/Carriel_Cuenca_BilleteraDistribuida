// Paquete de comunicación con el servidor.
package com.example.carriel_cuenca_billeteradistribuida.data.remote

import com.example.carriel_cuenca_billeteradistribuida.util.logHilo
import kotlinx.coroutines.channels.awaitClose          // Mantiene vivo el callbackFlow hasta que se cancele.
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow            // Convierte "callbacks" en un Flow.
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket                               // La conexión WebSocket abierta.
import okhttp3.WebSocketListener                       // "Oyente": OkHttp llama a sus funciones cuando pasa algo.
import java.util.concurrent.TimeUnit

// Lo que puede pasar en el túnel, ya traducido para el Repository.
// "sealed interface": familia cerrada de eventos.
sealed interface EventoSocket {
    data object Abierto : EventoSocket                       // El túnel se abrió.
    data class Mensaje(val texto: String) : EventoSocket     // Llegó un mensaje (texto JSON).
}

// Cliente WebSocket basado en OkHttp.
// OkHttp avisa de los eventos con CALLBACKS (funciones que él llama en SU propio hilo).
// Con "callbackFlow" los convertimos en un Flow, que el Repository recolecta con corrutinas.
class WebSocketCliente(clienteBase: OkHttpClient) {

    // Cliente propio para el WebSocket, derivado del de Retrofit:
    // - sin el interceptor de logs HTTP (el túnel no es una petición normal),
    // - readTimeout(0): sin límite de espera, porque el túnel pasa mucho tiempo en silencio,
    // - pingInterval(15 s): OkHttp envía un "ping" cada 15 s; si no hay respuesta,
    //   da la conexión por perdida (así detectamos un servidor caído).
    private val cliente: OkHttpClient = clienteBase.newBuilder()
        .apply { interceptors().clear() }
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    // Abre el WebSocket y devuelve un Flow con sus eventos.
    // - El Flow TERMINA normalmente si el servidor cierra el túnel.
    // - El Flow FALLA (lanza la excepción) si la conexión se cae o no se puede abrir.
    // - Si quien lo recolecta se cancela, awaitClose cierra el WebSocket.
    fun escuchar(url: String): Flow<EventoSocket> = callbackFlow {
        val oyente = object : WebSocketListener() {      // "object :" = clase anónima que hereda del oyente.

            // Se llama cuando el servidor acepta la conexión.
            override fun onOpen(webSocket: WebSocket, response: Response) {
                logHilo("WebSocket: túnel ABIERTO con $url")
                trySend(EventoSocket.Abierto)            // trySend: envía al Flow sin suspender.
            }

            // Se llama cada vez que el servidor envía un mensaje de texto.
            override fun onMessage(webSocket: WebSocket, text: String) {
                logHilo("WebSocket: mensaje recibido $text")
                trySend(EventoSocket.Mensaje(text))
            }

            // El servidor pidió cerrar: respondemos cerrando también.
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            // El túnel se cerró de forma ordenada: terminamos el Flow.
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                logHilo("WebSocket: túnel CERRADO (código $code)")
                close()
            }

            // Falló la conexión (servidor apagado, red caída, ping sin respuesta...).
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                logHilo("WebSocket: FALLO ${t.javaClass.simpleName}: ${t.message}")
                close(t)                                 // El Flow termina CON error.
            }
        }

        // Pedimos a OkHttp que abra el túnel. OkHttp trabaja en sus propios hilos y llama al oyente.
        val socket = cliente.newWebSocket(Request.Builder().url(url).build(), oyente)

        // Esperamos aquí (suspendidos) hasta que el Flow se cierre o se cancele;
        // entonces cerramos el WebSocket (código 1000 = cierre normal).
        awaitClose { socket.close(1000, "Cliente detenido") }
    }
}
