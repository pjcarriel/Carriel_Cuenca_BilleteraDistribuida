// Paquete "data.remote": todo lo que se comunica con el SERVIDOR por la red.
package com.example.carriel_cuenca_billeteradistribuida.data.remote

// Configuración de la conexión con el servidor Ktor.
// (Desde la Fase 6.5 la cuenta ya no es fija: llega con la SESIÓN del usuario que inicia sesión).
object ConfigServidor {

    // 10.0.2.2 es una dirección ESPECIAL del emulador: significa "la computadora anfitriona".
    // Si se usa un CELULAR REAL, aquí iría la IP de la PC en la red Wi-Fi (ej. 192.168.1.50).
    // Puerto 8090: el mismo que usa el servidor Ktor (Application.kt).
    const val URL_BASE = "http://10.0.2.2:8090/"

    // Dirección del WebSocket. "ws://" = WebSocket sin cifrar. El token va en la cabecera.
    const val URL_WEBSOCKET = "ws://10.0.2.2:8090/ws"
}
