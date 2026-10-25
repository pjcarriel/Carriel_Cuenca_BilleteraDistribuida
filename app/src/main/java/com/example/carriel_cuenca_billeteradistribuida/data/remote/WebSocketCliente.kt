// =====================================================================
//   Abre un "túnel" (WebSocket) que se queda conectado con el servidor,
//   para que nos avise al instante, por ejemplo, cuando nos transfieren dinero.
// =====================================================================

package com.example.carriel_cuenca_billeteradistribuida.data.remote

import com.example.carriel_cuenca_billeteradistribuida.util.logHilo
import kotlinx.coroutines.channels.awaitClose          // Mantiene el túnel abierto hasta que se cierre.
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow            // Convierte los avisos del túnel en una lista que va llegando.
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

// Código que manda el servidor cuando cierra el túnel porque la sesión no vale.
private const val CODIGO_SESION_RECHAZADA = 1008

// Lo que puede pasar en el túnel: se ABRIÓ o llegó un MENSAJE.
sealed interface EventoSocket {
    data object Abierto : EventoSocket
    data class Mensaje(val texto: String) : EventoSocket
}

// Error de "sesión rechazada": no se reintenta, hay que volver al login.
class SesionRechazadaException(motivo: String) : Exception(motivo)

// Abre el túnel con el servidor y escucha lo que llega.
class WebSocketCliente(clienteBase: OkHttpClient) {

    // Conexión propia del túnel: sin tiempo límite de espera y con un "ping" cada 15 s
    // para comprobar que sigue viva. El token se pone a mano más abajo.
    private val cliente: OkHttpClient = clienteBase.newBuilder()
        .apply { interceptors().clear() }
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    // Abre el túnel con el token y entrega cada aviso que llega.
    // Si la conexión se cae, termina con error para que el Repository reintente.
    fun escuchar(url: String, token: String): Flow<EventoSocket> = callbackFlow {
        // "Oyente": recibe lo que pasa en el túnel.
        val oyente = object : WebSocketListener() {
            // El túnel se conectó.
            override fun onOpen(webSocket: WebSocket, response: Response) {
                logHilo("WebSocket: túnel ABIERTO con $url")
                trySend(EventoSocket.Abierto)
            }

            // Llegó un mensaje del servidor: lo pasamos al Repository.
            override fun onMessage(webSocket: WebSocket, text: String) {
                logHilo("WebSocket: mensaje recibido $text")
                trySend(EventoSocket.Mensaje(text))
            }

            // El servidor pidió cerrar. Si fue por la sesión (1008), avisamos con el error especial.
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)              // 1000 = cierre normal.
                if (code == CODIGO_SESION_RECHAZADA) {
                    logHilo("WebSocket: sesión RECHAZADA por el servidor ($reason)")
                    close(SesionRechazadaException(reason))
                }
            }

            // El túnel quedó cerrado: dejamos de escuchar.
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                logHilo("WebSocket: túnel CERRADO (código $code)")
                close()
            }

            // Falló la red (servidor apagado, sin internet): terminamos con error para reintentar.
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                logHilo("WebSocket: FALLO ${t.javaClass.simpleName}: ${t.message}")
                close(t)
            }
        }

        // Pedimos abrir el túnel enviando el token.
        val peticion = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
        val socket = cliente.newWebSocket(peticion, oyente)

        // Esperamos aquí; cuando ya nadie escucha (cerrar sesión, salir), cerramos el túnel.
        awaitClose { socket.close(1000, "Cliente detenido") }
    }
}
