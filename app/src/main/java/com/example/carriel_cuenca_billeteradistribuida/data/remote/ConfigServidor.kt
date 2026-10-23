// Paquete "data.remote": todo lo que se comunica con el SERVIDOR por la red.
package com.example.carriel_cuenca_billeteradistribuida.data.remote

import android.content.Context
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull     // Convierte un texto en URL (o null si no es válida).

// Configuración de la conexión con el servidor Ktor.
// La DIRECCIÓN es configurable porque la misma app corre en dos sitios distintos:
//   - En el EMULADOR, el servidor de la PC se alcanza con 10.0.2.2 (valor por defecto).
//   - En un TELÉFONO REAL, hay que poner la IP de la PC en la red wifi (ej. 192.168.1.199).
// Se guarda en las preferencias de la app (SharedPreferences), propias de cada dispositivo.
object ConfigServidor {

    const val HOST_DEFECTO = "10.0.2.2"      // El emulador llega así a la PC anfitriona.
    const val PUERTO = 8090

    private const val PREFS = "config_servidor"
    private const val CLAVE_HOST = "host"

    // @Volatile: lo leen varios hilos (OkHttp) y lo escribe la UI; todos ven el valor actual.
    @Volatile
    private var host: String = HOST_DEFECTO

    // Lee la dirección guardada. Lee un archivo del DISCO: se llama desde Dispatchers.IO
    // (SesionRepository.sesionGuardada), nunca desde el hilo main.
    fun cargar(context: Context) {
        host = prefs(context).getString(CLAVE_HOST, HOST_DEFECTO) ?: HOST_DEFECTO
    }

    // Limpia lo que escribió el usuario: quita "http://", "ws://", la "/" final y el puerto.
    // Ej. "http://192.168.1.5:8090/" -> "192.168.1.5". Devuelve null si no es una dirección válida.
    fun normalizar(texto: String): String? {
        val limpio = texto.trim()
            .substringAfter("://")
            .substringBefore("/")
            .substringBefore(":")
            .ifEmpty { HOST_DEFECTO }
        // Si OkHttp no puede armar una URL con ese host (espacios, símbolos raros...), no es válido.
        return limpio.takeIf { "http://$it:$PUERTO/".toHttpUrlOrNull() != null }
    }

    // Guarda una dirección nueva (desde la pantalla de login). Devuelve false si no es válida
    // (así una dirección mal escrita nunca llega a Retrofit, que lanzaría una excepción).
    fun guardarHost(context: Context, nuevo: String): Boolean {
        host = normalizar(nuevo) ?: return false
        prefs(context).edit().putString(CLAVE_HOST, host).apply()   // apply() escribe en segundo plano.
        return true
    }

    fun host(): String = host
    fun urlBase(): String = "http://$host:$PUERTO/"          // Para Retrofit (REST).
    fun urlWebSocket(): String = "ws://$host:$PUERTO/ws"     // Para el WebSocket.

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
