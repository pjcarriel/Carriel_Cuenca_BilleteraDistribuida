// =====================================================================
//   Los 5 tipos de operación de la billetera (depósito, retiro, etc.).
//   Cada tipo sabe qué nombre mostrar y si el dinero entra o sale.
// =====================================================================

package com.example.carriel_cuenca_billeteradistribuida.model

// Lista fija de tipos de operación: no se puede inventar otro.
// etiqueta = texto en pantalla; esIngreso = true si entra dinero (verde), false si sale (rojo).
enum class TipoOperacion(
    val etiqueta: String,   // Texto que se ve en pantalla.
    val esIngreso: Boolean  // Verdadero = entra dinero; falso = sale dinero.
) {
    // El usuario recarga su cuenta. Entra dinero.
    DEPOSITO("Depósito", true),

    // El usuario saca efectivo. Sale dinero.
    RETIRO("Retiro", false),

    // El usuario envía dinero a otra cuenta. Sale dinero.
    TRANSFERENCIA_ENVIADA("Transferencia enviada", false),

    // Otra persona le envía dinero al usuario. Entra dinero.
    TRANSFERENCIA_RECIBIDA("Transferencia recibida", true),

    // El usuario paga luz, agua, internet u otro servicio. Sale dinero.
    PAGO_SERVICIO("Pago de servicio", false)
}
