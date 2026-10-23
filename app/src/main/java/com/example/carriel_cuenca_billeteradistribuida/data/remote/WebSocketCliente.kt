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
// "sealed" = solo existen estos dos casos; "data object" = un caso sin datos (hay una única instancia).
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
    // callbackFlow: OkHttp avisa con "callbacks" (onOpen, onMessage...) desde SUS hilos; callbackFlow
    // los convierte en un Flow que el Repository recorre con collect.
    //  - trySend(x) = mete x en el Flow (sin suspender; los callbacks no son corrutinas).
    //  - close() = termina el Flow normalmente; close(error) = lo termina lanzando ese error a quien hace collect.
    // Es "frío": el túnel se abre cuando alguien hace collect, no al llamar a escuchar().
    fun escuchar(url: String, token: String): Flow<EventoSocket> = callbackFlow {
        // "object : WebSocketListener()" = objeto ANÓNIMO que hereda de la clase y sobrescribe sus métodos.
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
                // Respondemos el cierre con 1000 (= cierre NORMAL), como pide el protocolo WebSocket.
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

            // Fallo de red (servidor apagado, sin internet, el ping no tuvo respuesta): cerramos el Flow
            // CON el error, para que el Repository lo atrape y programe un reintento.
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                logHilo("WebSocket: FALLO ${t.javaClass.simpleName}: ${t.message}")
                close(t)
            }
        }

        // La cabecera Authorization identifica al usuario (igual que en REST).
        val peticion = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
        val socket = cliente.newWebSocket(peticion, oyente)

        // Al cancelar quien escucha (cerrar sesión, salir de la app), cerramos el túnel.
        // awaitClose SUSPENDE aquí hasta que el Flow se cierre o se cancele; sin él, callbackFlow
        // terminaría de inmediato. Su bloque es la "limpieza" que se ejecuta al final.
        awaitClose { socket.close(1000, "Cliente detenido") }
    }
}
