// =====================================================================
//   Lo que se ve en el panel de administración de servicios (solo
//   laboratorio): la lista de facturas y los filtros elegidos.
// =====================================================================

package com.example.carriel_cuenca_billeteradistribuida.model

// Estado del panel de administración.
data class EstadoAdmin(
    val cargando: Boolean = false,              // true = se ve la ruedita mientras carga.
    val facturas: List<Factura> = emptyList(),  // Facturas que se ven en la lista.
    val estado: String? = null,                 // Filtro: "PENDIENTES", "PAGADAS" o vacío = todas.
    val servicio: String? = null,               // Filtro: "LUZ", "AGUA", "INTERNET" o vacío = todos.
    val error: String? = null,                  // Mensaje de error al cargar (vacío = sin error).
    val abierto: Boolean = false                // true si el panel está abierto (se recarga solo).
)
