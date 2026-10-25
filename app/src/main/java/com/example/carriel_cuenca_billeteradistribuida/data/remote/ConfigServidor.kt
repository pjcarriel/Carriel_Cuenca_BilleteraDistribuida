// =====================================================================
//   Guarda la DIRECCIÓN del servidor: 10.0.2.2 en el emulador, la IP de la
//   PC en un teléfono real, o la del túnel de Cloudflare desde internet.
// =====================================================================

package com.example.carriel_cuenca_billeteradistribuida.data.remote

import android.content.Context
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull     // Revisa si un texto es una dirección web válida.

// Configuración de la dirección del servidor; se guarda en el teléfono.
object ConfigServidor {

    const val HOST_DEFECTO = "10.0.2.2"      // Dirección con la que el emulador llega a la PC.
    const val PUERTO = 8090                  // Puerto donde escucha el servidor.

    private const val PREFS = "config_servidor"
    private const val CLAVE_HOST = "host"

    // Dirección actual: corta ("192.168.1.199") o completa ("https://algo.trycloudflare.com").
    @Volatile
    private var host: String = HOST_DEFECTO

    // Lee la dirección guardada en el teléfono.
    fun cargar(context: Context) {
        host = prefs(context).getString(CLAVE_HOST, HOST_DEFECTO) ?: HOST_DEFECTO
    }

    // Limpia lo que escribió el usuario. Ej: "http://192.168.1.5:8090/" -> "192.168.1.5".
    // Si no es una dirección válida, devuelve null.
    fun normalizar(texto: String): String? {
        val limpio = texto.trim().ifEmpty { HOST_DEFECTO }
        // Le ponemos el inicio correcto ("http://" o "https://").
        val conEsquema = when {
            limpio.startsWith("wss://", ignoreCase = true) -> "https://" + limpio.substring(6)
            limpio.startsWith("ws://", ignoreCase = true) -> "http://" + limpio.substring(5)
            "://" in limpio -> limpio
            else -> "http://$limpio"
        }
        // Revisamos que sea una dirección válida.
        val url = conEsquema.toHttpUrlOrNull() ?: return null
        // ¿El usuario escribió un puerto?
        val puertoEscrito = Regex("^[A-Za-z]+://[^/]*:(\\d+)").find(conEsquema)?.groupValues?.get(1)?.toInt()
        return when {
            // Túnel por internet (https): forma completa.
            url.scheme == "https" -> if (url.port == 443) "https://${url.host}" else "https://${url.host}:${url.port}"
            // Puerto distinto de 8090: forma completa para respetarlo.
            puertoEscrito != null && puertoEscrito != PUERTO -> "http://${url.host}:$puertoEscrito"
            // Caso normal: solo la IP.
            else -> url.host
        }
    }

    // Guarda una dirección nueva desde el login; devuelve false si está mal escrita.
    fun guardarHost(context: Context, nuevo: String): Boolean {
        host = normalizar(nuevo) ?: return false
        prefs(context).edit().putString(CLAVE_HOST, host).apply()
        return true
    }

    // Devuelve la dirección guardada.
    fun host(): String = host

    // Dirección para los pedidos normales. Ej: "http://10.0.2.2:8090/".
    fun urlBase(): String =
        if ("://" in host) "$host/" else "http://$host:$PUERTO/"

    // Dirección del túnel WebSocket. Ej: "ws://10.0.2.2:8090/ws".
    fun urlWebSocket(): String =
        urlBase().replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") + "ws"

    // Abre el archivo pequeño del teléfono donde se guarda la dirección.
    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
