// =====================================================================
//   Un "Movimiento" es una operación que YA se hizo (depósito, retiro,
//   transferencia o pago). Es lo que aparece en el historial y en el comprobante.
// =====================================================================

package com.example.carriel_cuenca_billeteradistribuida.model


const val CATEGORIA_SERVICIO_BASICO = "Servicio Básico"


data class Movimiento(
    // Número que identifica al movimiento. Empieza en 0 y luego
    // la base de datos le pone el número real.
    val id: Long = 0,

    // Qué operación fue: depósito, retiro, transferencia, pago, etc.
    val tipo: TipoOperacion,

    // Cuánto dinero se movió (siempre positivo; el color en pantalla
    // indica si entró o salió).
    val monto: Double,

    // Fecha y hora en que se hizo la operación.
    val fechaHora: Long,

    // Información extra, por ejemplo la cuenta destino o "Contrato LUZ-12345".
    val detalle: String,

    // Categoría del movimiento. Solo los pagos de servicio la tienen
    // ("Servicio Básico"); en los demás queda vacía (null).
    val categoria: String? = null,

    // Número que el servidor le da a la operación cuando la guarda.
    // Puede estar vacío (null) si la operación no pasó por el servidor.
    val transaccionId: Long? = null
)
