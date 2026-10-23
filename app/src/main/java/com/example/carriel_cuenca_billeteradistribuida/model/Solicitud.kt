// Paquete de los modelos de datos.
package com.example.carriel_cuenca_billeteradistribuida.model

// Lo que el usuario PIDE hacer al confirmar un diálogo.
// "sealed interface" = familia CERRADA: solo existen estas 4 solicitudes,
// y cada una lleva SOLO los datos que necesita (un depósito no tiene cuenta destino).
// Así es imposible armar, por ejemplo, una transferencia sin destino.
sealed interface Solicitud {
    val monto: Double            // Todas las solicitudes tienen monto.
    val tipo: TipoOperacion      // Y cada una sabe a qué tipo de movimiento corresponde.

    // "override" = cada clase da su propio valor a las propiedades que exige la interfaz.
    // Depósito: solo el monto.
    data class Deposito(override val monto: Double) : Solicitud {
        override val tipo = TipoOperacion.DEPOSITO
    }

    // Retiro: solo el monto.
    data class Retiro(override val monto: Double) : Solicitud {
        override val tipo = TipoOperacion.RETIRO
    }

    // Transferencia: monto y cuenta que recibe.
    data class Transferencia(val cuentaDestino: String, override val monto: Double) : Solicitud {
        override val tipo = TipoOperacion.TRANSFERENCIA_ENVIADA
    }

    // Pago de servicio: tipo de servicio (LUZ, AGUA, INTERNET), código de contrato y monto.
    data class PagoServicio(val servicio: String, val contrato: String, override val monto: Double) : Solicitud {
        override val tipo = TipoOperacion.PAGO_SERVICIO
    }
}
