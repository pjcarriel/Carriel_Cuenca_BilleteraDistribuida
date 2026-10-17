// Paquete de los modelos de datos.
package com.example.carriel_cuenca_billeteradistribuida.model

// Factura de un servicio básico, consultada al servidor ANTES de pagarla.
// El usuario no escribe el monto: lo define la factura.
data class Factura(
    val codigo: String,         // Código de contrato (ej. "445566").
    val servicio: String,       // "LUZ", "AGUA" o "INTERNET".
    val titular: String,        // A nombre de quién está el contrato.
    val monto: Double,          // Lo que se debe pagar.
    val pagado: Boolean         // true = ya se pagó (no se puede pagar otra vez).
)
