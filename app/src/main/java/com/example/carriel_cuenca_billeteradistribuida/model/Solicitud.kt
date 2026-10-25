// =====================================================================
//   Una "Solicitud" es el PEDIDO que hace el usuario cuando aprieta el
//   botón "Confirmar" en una ventana de operación.
// =====================================================================

package com.example.carriel_cuenca_billeteradistribuida.model

// Solicitud = el pedido del usuario. Solo hay 4 tipos de pedido posibles
// (los de abajo). Todos tienen dos cosas en común:
sealed interface Solicitud {
    val monto: Double            // 1. Cuánto dinero.
    val tipo: TipoOperacion      // 2. Con qué etiqueta se guardará en el historial.

    // Pedido de DEPÓSITO: solo hace falta saber cuánto.
    // Al guardarse en el historial aparecerá como "Depósito".
    data class Deposito(override val monto: Double) : Solicitud {
        override val tipo = TipoOperacion.DEPOSITO
    }

    // Pedido de RETIRO: solo hace falta saber cuánto.
    // Al guardarse en el historial aparecerá como "Retiro".
    data class Retiro(override val monto: Double) : Solicitud {
        override val tipo = TipoOperacion.RETIRO
    }

    // Pedido de TRANSFERENCIA: hace falta saber cuánto y a qué cuenta va.
    // Al guardarse en el historial aparecerá como "Transferencia enviada".
    data class Transferencia(val cuentaDestino: String, override val monto: Double) : Solicitud {
        override val tipo = TipoOperacion.TRANSFERENCIA_ENVIADA
    }

    // Pedido de PAGO DE SERVICIO: hace falta saber qué servicio (LUZ, AGUA...),
    // el número de contrato y cuánto se paga.
    // Al guardarse en el historial aparecerá como "Pago de servicio".
    data class PagoServicio(val servicio: String, val contrato: String, override val monto: Double) : Solicitud {
        override val tipo = TipoOperacion.PAGO_SERVICIO
    }
}
