// =====================================================================
//   Los números que muestra la pantalla de pruebas de concurrencia,
//   donde se lanzan muchas peticiones al mismo tiempo.
// =====================================================================

package com.example.carriel_cuenca_billeteradistribuida.model

// Qué se pone a prueba: el servidor o la base de datos del teléfono.
enum class TipoPrueba {
    SERVIDOR,   // Muchas consultas al servidor a la vez.
    ROOM        // Muchas lecturas a la base de datos del teléfono a la vez.
}

// Lo que se ve en el panel de pruebas. Empieza todo en cero.
data class EstadoPruebas(
    val ejecutando: Boolean = false,          // true mientras corre la prueba.
    val total: Int = 0,                       // Cuántas peticiones se lanzaron.
    val completadas: Int = 0,                 // Cuántas ya terminaron (sube en vivo).
    val hilos: Map<String, Int> = emptyMap(), // Cada hilo y cuántas peticiones hizo.
    val hiloRespuesta: String = "",           // Hilo que muestra los resultados (debe ser "main").
    val tiempoMs: Long = 0,                   // Cuánto tardó todo, en milisegundos.
    val fallidas: Int = 0                     // Cuántas fallaron (en transferencias).
)
