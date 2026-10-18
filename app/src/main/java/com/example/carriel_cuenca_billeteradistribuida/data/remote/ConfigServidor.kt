// Paquete "data.remote": todo lo que se comunica con el SERVIDOR por la red.
package com.example.carriel_cuenca_billeteradistribuida.data.remote

import android.content.Context

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

    // Se llama al abrir la app (MainActivity) para recuperar la dirección guardada.
    fun cargar(context: Context) {
        host = prefs(context).getString(CLAVE_HOST, HOST_DEFECTO) ?: HOST_DEFECTO
    }

    // Guarda una dirección nueva (desde la pantalla de login).
    fun guardarHost(context: Context, nuevo: String) {
        host = nuevo.trim().ifEmpty { HOST_DEFECTO }
        prefs(context).edit().putString(CLAVE_HOST, host).apply()
    }

    fun host(): String = host
    fun urlBase(): String = "http://$host:$PUERTO/"          // Para Retrofit (REST).
    fun urlWebSocket(): String = "ws://$host:$PUERTO/ws"     // Para el WebSocket.

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
