// =====================================================================
//   Una factura de servicio básico (luz, agua, internet...). La app la
//   consulta antes de pagar, así el monto lo pone la factura.
// =====================================================================

package com.example.carriel_cuenca_billeteradistribuida.model

// Datos de una factura tal como llegan del servidor.
data class Factura(
    val codigo: String,         // Número de contrato (ej. "445566").
    val servicio: String,       // "LUZ", "AGUA", "INTERNET"...
    val titular: String,        // A nombre de quién está.
    val monto: Double,          // Cuánto se debe pagar.
    val pagado: Boolean,        // true = ya se pagó (no se puede pagar otra vez).
    // Datos extra; pueden venir vacíos en facturas antiguas.
    val cedula: String? = null,     // Cédula del titular.
    val periodo: String? = null,    // Mes que se cobra (ej. "Octubre 2026").
    val pagadoPor: String? = null,  // Cuenta que la pagó (si ya está pagada).
    val fechaPago: Long? = null     // Fecha y hora del pago (si ya está pagada).
)
