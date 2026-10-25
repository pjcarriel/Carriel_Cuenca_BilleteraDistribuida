// =====================================================================
//   Cuántos teléfonos (nodos) están conectados al servidor ahora mismo
//   y de qué cuentas son. Solo lo ve el usuario laboratorio.
// =====================================================================

package com.example.carriel_cuenca_billeteradistribuida.model

// Foto del momento: total de teléfonos conectados y sus cuentas.
data class NodosConectados(
    val total: Int,                 // Teléfonos conectados (una cuenta puede estar en varios).
    val cuentas: List<String>       // Cuentas distintas conectadas (ej. "1001", "9999").
)
