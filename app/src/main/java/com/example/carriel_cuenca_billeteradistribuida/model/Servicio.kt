// Paquete de los modelos de datos.
package com.example.carriel_cuenca_billeteradistribuida.model

// Un SERVICIO BÁSICO del catálogo del servidor (luz, agua, internet... y los que agregue el laboratorio).
data class Servicio(
    val codigo: String,     // Código corto en mayúsculas (ej. "LUZ", "GAS"). Es lo que se guarda en cada factura.
    val nombre: String,     // Nombre para mostrar (ej. "Gas doméstico").
    val icono: String       // Clave del ícono: RAYO, GOTA, WIFI, TELEFONO, GAS, TV, BASURA u OTRO.
)

// Servicios que existen desde el inicio. Se usan si todavía no se pudo descargar el catálogo
// (por ejemplo, sin conexión), para que el diálogo de pago siempre tenga opciones.
val SERVICIOS_BASE = listOf(
    Servicio("LUZ", "Luz eléctrica", "RAYO"),
    Servicio("AGUA", "Agua potable", "GOTA"),
    Servicio("INTERNET", "Internet", "WIFI")
)
