// Paquete de comunicación con el servidor.
package com.example.carriel_cuenca_billeteradistribuida.data.remote

import com.example.carriel_cuenca_billeteradistribuida.util.logHilo
import kotlinx.coroutines.channels.awaitClose          // Mantiene vivo el callbackFlow hasta que se cancele.
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow            // Convierte "callbacks" en un Flow.
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

// Código con el que el servidor cierra el túnel si el token no es válido o expiró (1008 = POLICY VIOLATION).
private const val CODIGO_SESION_RECHAZADA = 1008

// Lo que puede pasar en el túnel, ya traducido para el Repository.
sealed interface EventoSocket {
    data object Abierto : EventoSocket
    data class Mensaje(val texto: String) : EventoSocket
}

// El servidor rechazó el túnel por la sesión: NO hay que reintentar, hay que volver al login.
class SesionRechazadaException(motivo: String) : Exception(motivo)

// Cliente WebSocket basado en OkHttp: convierte sus callbacks en un Flow con callbackFlow.
class WebSocketCliente(clienteBase: OkHttpClient) {

    // Cliente propio para el túnel: sin interceptores (el token se pone a mano abajo),
    // sin límite de lectura (el túnel pasa mucho tiempo en silencio) y con ping cada 15 s.
    private val cliente: OkHttpClient = clienteBase.newBuilder()
        .apply { interceptors().clear() }
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    // Abre el túnel con el token en la cabecera y devuelve un Flow con sus eventos.
    // - Termina normal si el servidor cierra; termina con error si la conexión falla;
    // - termina con SesionRechazadaException si el servidor rechaza la sesión (código 1008).
    fun escuchar(url: String, token: String): Flow<EventoSocket> = callbackFlow {
        val oyente = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                logHilo("WebSocket: túnel ABIERTO con $url")
                trySend(EventoSocket.Abierto)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                logHilo("WebSocket: mensaje recibido $text")
                trySend(EventoSocket.Mensaje(text))
            }

            // El servidor pidió cerrar. Si fue por la sesión (1008), avisamos con una excepción especial.
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                if (code == CODIGO_SESION_RECHAZADA) {
                    logHilo("WebSocket: sesión RECHAZADA por el servidor ($reason)")
                    close(SesionRechazadaException(reason))
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                logHilo("WebSocket: túnel CERRADO (código $code)")
                close()                                  // Si ya estaba cerrado, no hace nada.
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                logHilo("WebSocket: FALLO ${t.javaClass.simpleName}: ${t.message}")
                close(t)
            }
        }

        // La cabecera Authorization identifica al usuario (igual que en REST).
        val peticion = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
        val socket = cliente.newWebSocket(peticion, oyente)

        // Al cancelar quien escucha (cerrar sesión, salir de la app), cerramos el túnel.
        awaitClose { socket.close(1000, "Cliente detenido") }
    }
}
