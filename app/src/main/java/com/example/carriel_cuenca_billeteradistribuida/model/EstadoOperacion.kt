// =====================================================================
//   En qué paso está la operación que pidió el usuario: sin hacer nada,
//   procesando, salió bien o falló.
// =====================================================================

package com.example.carriel_cuenca_billeteradistribuida.model

// Los 4 pasos posibles. Exito y Error llevan un mensaje para el usuario.
sealed class EstadoOperacion {

    // No hay ninguna operación en curso.
    data object Inactivo : EstadoOperacion()

    // Esperando al servidor: se ve la ruedita y el botón queda bloqueado.
    data object Procesando : EstadoOperacion()

    // Salió bien: se muestra el mensaje (ej. "Depósito realizado").
    data class Exito(val mensaje: String) : EstadoOperacion()

    // Falló: se muestra el motivo (ej. "Saldo insuficiente").
    data class Error(val mensaje: String) : EstadoOperacion()
}
