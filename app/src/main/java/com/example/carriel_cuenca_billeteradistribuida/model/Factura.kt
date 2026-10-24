// Paquete de los modelos de datos.
package com.example.carriel_cuenca_billeteradistribuida.model

// Factura de un servicio básico, consultada al servidor ANTES de pagarla.
// El usuario no escribe el monto: lo define la factura.
data class Factura(
    val codigo: String,         // Código de contrato (ej. "445566").
    val servicio: String,       // "LUZ", "AGUA" o "INTERNET".
    val titular: String,        // A nombre de quién está el contrato.
    val monto: Double,          // Lo que se debe pagar.
    val pagado: Boolean,        // true = ya se pagó (no se puede pagar otra vez).
    // Fase 8 (con valor por defecto: las facturas antiguas pueden no tenerlos).
    val cedula: String? = null,     // Cédula del titular (ej. "1712345675").
    val periodo: String? = null,    // Mes facturado (ej. "Octubre 2026").
    val pagadoPor: String? = null,  // Cuenta que la pagó (solo si pagado = true).
    val fechaPago: Long? = null     // Cuándo se pagó, en milisegundos (solo si pagado = true).
)
