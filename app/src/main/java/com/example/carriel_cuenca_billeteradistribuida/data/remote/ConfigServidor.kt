// Paquete "data.remote": todo lo que se comunica con el SERVIDOR por la red.
package com.example.carriel_cuenca_billeteradistribuida.data.remote

import android.content.Context
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull     // Convierte un texto en URL (o null si no es válida).

// Configuración de la conexión con el servidor Ktor.
// La DIRECCIÓN es configurable porque la misma app corre en sitios distintos:
//   - En el EMULADOR, el servidor de la PC se alcanza con 10.0.2.2 (valor por defecto).
//   - En un TELÉFONO REAL en la misma wifi, con la IP de la PC (ej. 192.168.1.199).
//   - Desde CUALQUIER RED, con la dirección pública del túnel de Cloudflare
//     (ej. https://algo.trycloudflare.com), que lleva al servidor de la PC por internet.
// Se guarda en las preferencias de la app (SharedPreferences), propias de cada dispositivo.
object ConfigServidor {

    const val HOST_DEFECTO = "10.0.2.2"      // El emulador llega así a la PC anfitriona.
    const val PUERTO = 8090

    private const val PREFS = "config_servidor"
    private const val CLAVE_HOST = "host"

    // Dirección guardada, en una de dos formas:
    //   - CORTA: solo el host ("10.0.2.2", "192.168.1.199") -> http://host:8090 (red local).
    //   - COMPLETA: con esquema ("https://algo.trycloudflare.com") -> se usa tal cual (internet).
    // @Volatile: lo leen varios hilos (OkHttp) y lo escribe la UI; todos ven el valor actual.
    @Volatile
    private var host: String = HOST_DEFECTO

    // Lee la dirección guardada. Lee un archivo del DISCO: se llama desde Dispatchers.IO
    // (SesionRepository.sesionGuardada), nunca desde el hilo main.
    fun cargar(context: Context) {
        host = prefs(context).getString(CLAVE_HOST, HOST_DEFECTO) ?: HOST_DEFECTO
    }

    // Limpia lo que escribió el usuario y lo deja en la forma corta o completa. Ejemplos:
    //   "192.168.1.5"                      -> "192.168.1.5"
    //   "http://192.168.1.5:8090/"         -> "192.168.1.5"           (es el puerto de siempre)
    //   "https://algo.trycloudflare.com/"  -> "https://algo.trycloudflare.com"
    //   "wss://algo.trycloudflare.com/ws"  -> "https://algo.trycloudflare.com"
    // Devuelve null si no es una dirección válida.
    fun normalizar(texto: String): String? {
        val limpio = texto.trim().ifEmpty { HOST_DEFECTO }
        // Si no trae esquema, es la forma corta de siempre (http al puerto 8090).
        val conEsquema = when {
            limpio.startsWith("wss://", ignoreCase = true) -> "https://" + limpio.substring(6)
            limpio.startsWith("ws://", ignoreCase = true) -> "http://" + limpio.substring(5)
            "://" in limpio -> limpio
            else -> "http://$limpio"
        }
        // OkHttp valida la URL (espacios, símbolos raros, esquema desconocido -> null).
        val url = conEsquema.toHttpUrlOrNull() ?: return null
        // ¿El usuario escribió un puerto? (OkHttp pone 80/443 por defecto si no lo escribió.)
        // La expresión busca ":número" justo después del host; groupValues[1] es ese número (o null si no hay).
        val puertoEscrito = Regex("^[A-Za-z]+://[^/]*:(\\d+)").find(conEsquema)?.groupValues?.get(1)?.toInt()
        return when {
            // HTTPS (túnel): forma completa; el puerto solo si no es el estándar 443.
            url.scheme == "https" -> if (url.port == 443) "https://${url.host}" else "https://${url.host}:${url.port}"
            // HTTP con un puerto distinto de 8090: forma completa para respetarlo.
            puertoEscrito != null && puertoEscrito != PUERTO -> "http://${url.host}:$puertoEscrito"
            // HTTP sin puerto o con 8090: forma corta de siempre.
            else -> url.host
        }
    }

    // Guarda una dirección nueva (desde la pantalla de login). Devuelve false si no es válida
    // (así una dirección mal escrita nunca llega a Retrofit, que lanzaría una excepción).
    fun guardarHost(context: Context, nuevo: String): Boolean {
        host = normalizar(nuevo) ?: return false
        prefs(context).edit().putString(CLAVE_HOST, host).apply()   // apply() escribe en segundo plano.
        return true
    }

    fun host(): String = host

    // Dirección base para Retrofit (REST).
    fun urlBase(): String =
        if ("://" in host) "$host/" else "http://$host:$PUERTO/"

    // Dirección del WebSocket: el mismo servidor con ws:// (o wss:// si es HTTPS, cifrado).
    fun urlWebSocket(): String =
        urlBase().replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") + "ws"

    // SharedPreferences = pequeño archivo "clave=valor" de la app. MODE_PRIVATE: solo esta app lo lee.
    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
