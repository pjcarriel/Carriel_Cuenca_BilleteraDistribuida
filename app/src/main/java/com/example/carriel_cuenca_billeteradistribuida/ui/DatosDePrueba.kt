// Paquete de la interfaz gráfica.
package com.example.carriel_cuenca_billeteradistribuida.ui

// Importamos los modelos que creamos en el paquete "model".
import com.example.carriel_cuenca_billeteradistribuida.model.CATEGORIA_SERVICIO_BASICO
import com.example.carriel_cuenca_billeteradistribuida.model.Movimiento
import com.example.carriel_cuenca_billeteradistribuida.model.TipoOperacion

// DATOS FALSOS solo para la Fase 1 (ver cómo luce la pantalla).
// En la Fase 2 el historial vendrá de Room y este archivo se borrará.
// "object" = una clase de la que existe UNA SOLA instancia (no se usa "new" ni constructor).
object DatosDePrueba {

    // Hora actual en milisegundos; "private" = solo se usa dentro de este object.
    private val ahora = System.currentTimeMillis()

    // Cuántos milisegundos tiene una hora (60 min * 60 seg * 1000 ms).
    private const val UNA_HORA = 60 * 60 * 1000L

    // Saldo inventado para la tarjeta superior.
    val saldo = 1250.75

    // Lista de movimientos inventados. "listOf" crea una lista que no se puede modificar.
    val movimientos = listOf(
        // Un depósito de hace 1 hora.
        Movimiento(id = 1, tipo = TipoOperacion.DEPOSITO, monto = 500.0,
            fechaHora = ahora - UNA_HORA, detalle = "Recarga de saldo"),
        // Un retiro de hace 3 horas.
        Movimiento(id = 2, tipo = TipoOperacion.RETIRO, monto = 80.0,
            fechaHora = ahora - 3 * UNA_HORA, detalle = "Cajero automático"),
        // Una transferencia que recibimos de otra cuenta.
        Movimiento(id = 3, tipo = TipoOperacion.TRANSFERENCIA_RECIBIDA, monto = 150.0,
            fechaHora = ahora - 5 * UNA_HORA, detalle = "De la cuenta 1002"),
        // Una transferencia que enviamos.
        Movimiento(id = 4, tipo = TipoOperacion.TRANSFERENCIA_ENVIADA, monto = 45.5,
            fechaHora = ahora - 24 * UNA_HORA, detalle = "A la cuenta 1003"),
        // Un pago de servicio: es el ÚNICO que lleva categoría (los demás quedan en null).
        Movimiento(id = 5, tipo = TipoOperacion.PAGO_SERVICIO, monto = 32.75,
            fechaHora = ahora - 30 * UNA_HORA, detalle = "Luz - Contrato 445566",
            categoria = CATEGORIA_SERVICIO_BASICO)
    )
}
