// Paquete de los modelos de datos.
package com.example.carriel_cuenca_billeteradistribuida.model

// Qué recurso golpea la prueba de concurrencia.
enum class TipoPrueba {
    SERVIDOR,   // Peticiones de red (Retrofit/OkHttp) -> Dispatchers.IO.
    ROOM        // Lecturas de la base de datos local (Room) -> Dispatchers.IO.
}

// Estado del panel de pruebas de concurrencia (lo observa la pantalla de pruebas).
data class EstadoPruebas(
    val ejecutando: Boolean = false,          // true mientras corren las N peticiones.
    val total: Int = 0,                       // Cuántas se lanzaron.
    val completadas: Int = 0,                 // Cuántas han terminado (sube en vivo).
    val hilos: Map<String, Int> = emptyMap(), // Hilo IO -> cuántas peticiones atendió.
    val hiloRespuesta: String = "",           // Hilo donde se procesan las respuestas (debe ser "main").
    val tiempoMs: Long = 0                    // Tiempo total de las N peticiones concurrentes.
)
