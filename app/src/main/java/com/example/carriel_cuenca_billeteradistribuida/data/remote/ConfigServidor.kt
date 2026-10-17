// Paquete "data.remote": todo lo que se comunica con el SERVIDOR por la red.
package com.example.carriel_cuenca_billeteradistribuida.data.remote

// Configuración de la conexión con el servidor Ktor.
// "object" = una sola instancia; "const val" = constante conocida al compilar.
object ConfigServidor {

    // 10.0.2.2 es una dirección ESPECIAL del emulador: significa "la computadora anfitriona"
    // (donde corre el servidor). "localhost" dentro del emulador sería el propio emulador.
    // Si se usa un CELULAR REAL, aquí iría la IP de la PC en la red Wi-Fi (ej. 192.168.1.50).
    // Puerto 8090: el mismo que usa el servidor Ktor (Application.kt).
    const val URL_BASE = "http://10.0.2.2:8090/"

    // Dirección del WebSocket (se usará en la Fase 5). "ws://" = WebSocket sin cifrar.
    const val URL_WEBSOCKET = "ws://10.0.2.2:8090/ws/"

    // Número de cuenta de ESTA app en el servidor (Pamela Carriel).
    const val CUENTA_APP = "1001"
}
