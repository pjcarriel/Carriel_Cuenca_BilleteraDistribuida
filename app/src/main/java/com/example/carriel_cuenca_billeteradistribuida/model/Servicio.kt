// =====================================================================
//   Los servicios básicos que se pueden pagar (luz, agua, internet y
//   los que agregue el laboratorio).
// =====================================================================

package com.example.carriel_cuenca_billeteradistribuida.model

// Un servicio del catálogo: código, nombre e ícono.
data class Servicio(
    val codigo: String,     // Código corto (ej. "LUZ", "GAS"). Se guarda en cada factura.
    val nombre: String,     // Nombre que ve el usuario (ej. "Gas doméstico").
    val icono: String       // Qué dibujito mostrar: RAYO, GOTA, WIFI, TELEFONO, GAS, TV, BASURA u OTRO.
)

// Los 3 servicios de siempre. Se usan si todavía no se descargó el catálogo (ej. sin internet).
val SERVICIOS_BASE = listOf(
    Servicio("LUZ", "Luz eléctrica", "RAYO"),
    Servicio("AGUA", "Agua potable", "GOTA"),
    Servicio("INTERNET", "Internet", "WIFI")
)
