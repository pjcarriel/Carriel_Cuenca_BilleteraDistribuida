// Paquete de los modelos de datos.
package com.example.carriel_cuenca_billeteradistribuida.model

// Estado del PANEL DE ADMINISTRACIÓN DE SERVICIOS (solo laboratorio).
data class EstadoAdmin(
    val cargando: Boolean = false,              // true mientras se pide la lista al servidor.
    val facturas: List<Factura> = emptyList(),  // Facturas que cumplen los filtros.
    val estado: String? = null,                 // Filtro: "PENDIENTES", "PAGADAS" o null = todas.
    val servicio: String? = null,               // Filtro: "LUZ", "AGUA", "INTERNET" o null = todos.
    val error: String? = null,                  // Último error al cargar (null = sin error).
    val abierto: Boolean = false                // true si el panel se abrió (para recargar con los avisos).
)
