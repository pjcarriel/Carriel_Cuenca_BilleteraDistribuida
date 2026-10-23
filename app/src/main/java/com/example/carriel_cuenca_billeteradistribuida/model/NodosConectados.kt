// Paquete de los modelos de datos.
package com.example.carriel_cuenca_billeteradistribuida.model

// Cuántos NODOS (teléfonos) tienen el WebSocket abierto con el servidor en este momento.
// Solo lo recibe el usuario laboratorio; se actualiza en tiempo real.
data class NodosConectados(
    val total: Int,                 // Dispositivos conectados (una cuenta puede estar en varios).
    val cuentas: List<String>       // Cuentas distintas conectadas (ej. "1001", "9999").
)
