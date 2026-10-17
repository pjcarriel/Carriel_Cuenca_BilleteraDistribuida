// Paquete (carpeta lógica) donde viven los modelos de datos de la app.
package com.example.carriel_cuenca_billeteradistribuida.model

// "enum class" = una lista CERRADA de valores posibles.
// Una operación solo puede ser uno de estos 5 tipos; nunca un texto inventado.
// Cada valor lleva dos datos (definidos en el constructor de abajo):
//   - etiqueta:  el texto que se muestra en pantalla.
//   - esIngreso: true si el dinero ENTRA (verde), false si SALE (rojo).
enum class TipoOperacion(
    val etiqueta: String,   // "val" = propiedad de solo lectura (no se puede cambiar).
    val esIngreso: Boolean  // Boolean = verdadero o falso.
) {
    // Depósito: el usuario recarga su cuenta. Dinero que ENTRA.
    DEPOSITO("Depósito", true),

    // Retiro: el usuario saca efectivo. Dinero que SALE.
    RETIRO("Retiro", false),

    // Transferencia que YO envío a otra cuenta. Dinero que SALE.
    TRANSFERENCIA_ENVIADA("Transferencia enviada", false),

    // Transferencia que OTRO me envía (llegará por WebSocket). Dinero que ENTRA.
    TRANSFERENCIA_RECIBIDA("Transferencia recibida", true),

    // Pago de luz, agua o internet. Dinero que SALE.
    PAGO_SERVICIO("Pago de servicio", false)
}
