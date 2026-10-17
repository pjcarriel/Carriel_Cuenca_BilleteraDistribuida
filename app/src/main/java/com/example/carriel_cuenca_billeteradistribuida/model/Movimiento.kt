// Paquete de los modelos de datos.
package com.example.carriel_cuenca_billeteradistribuida.model

// Constante con el nombre de la categoría que pide el enunciado para los pagos.
// "const val" = valor fijo que se conoce al compilar y nunca cambia.
const val CATEGORIA_SERVICIO_BASICO = "Servicio Básico"

// "data class" = clase pensada solo para GUARDAR DATOS.
// Kotlin le genera automáticamente: toString() (para imprimirla),
// equals() (para compararla) y copy() (para crear una copia cambiando algo).
// Cada objeto Movimiento representa UN comprobante del historial.
data class Movimiento(
    // Identificador único. "= 0" es un valor por defecto: si no lo pasas, vale 0.
    // (Más adelante Room asignará el número real automáticamente.)
    val id: Long = 0,

    // Qué tipo de operación fue (uno de los 5 valores del enum).
    val tipo: TipoOperacion,

    // Cantidad de dinero de la operación (siempre positiva; el color dice si entra o sale).
    val monto: Double,

    // Fecha y hora en milisegundos desde 1970 (así se guardan fácilmente en la base de datos).
    val fechaHora: Long,

    // Texto extra: cuenta destino/origen o código de contrato. Ej: "Contrato LUZ-12345".
    val detalle: String,

    // "String?" = NULL SAFETY: el signo "?" indica que puede no tener valor (null).
    // Solo los pagos de servicio tendrán categoría ("Servicio Básico"); los demás quedan en null.
    val categoria: String? = null,

    // Id del comprobante en el SERVIDOR (MySQL). Sirve para no duplicar un movimiento
    // que llega dos veces (por WebSocket y al descargar el historial). null = sin id del servidor.
    val transaccionId: Long? = null
)
