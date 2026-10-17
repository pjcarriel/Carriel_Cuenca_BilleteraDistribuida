// Paquete de la interfaz gráfica.
package com.example.carriel_cuenca_billeteradistribuida.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions                 // Tipo de teclado del campo.
import androidx.compose.material3.AlertDialog                           // Cuadro de diálogo (modal).
import androidx.compose.material3.FilterChip                            // Botón tipo "píldora" seleccionable.
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField                     // Campo de texto con borde.
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf                          // Estado que Compose observa.
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType                      // Teclado numérico/decimal.
import androidx.compose.ui.unit.dp
import com.example.carriel_cuenca_billeteradistribuida.model.Solicitud
import com.example.carriel_cuenca_billeteradistribuida.model.TipoOperacion

// Servicios básicos que se pueden pagar (deben coincidir con los del servidor).
private val SERVICIOS = listOf("LUZ", "AGUA", "INTERNET")

// DIÁLOGO de una operación: pide el monto y, según el tipo, la cuenta destino o el contrato.
// No llama al servidor: solo arma una Solicitud y se la entrega a quien lo abrió (lambda onConfirmar).
@Composable
fun OperacionDialog(
    tipo: TipoOperacion,                     // Qué botón se tocó.
    onConfirmar: (Solicitud) -> Unit,        // Qué hacer con la solicitud lista.
    onCancelar: () -> Unit                   // Qué hacer al cerrar sin confirmar.
) {
    // Estado de cada campo. "remember { mutableStateOf(...) }" = valor que sobrevive a los
    // redibujados y que, al cambiar, hace que Compose redibuje el diálogo.
    // "by" + "var" = se lee y se escribe como una variable normal.
    var monto by remember { mutableStateOf("") }
    var cuentaDestino by remember { mutableStateOf("") }
    var servicio by remember { mutableStateOf(SERVICIOS.first()) }
    var contrato by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }    // Mensaje de validación (o null).

    // Título según el tipo ("when" como expresión).
    val titulo = when (tipo) {
        TipoOperacion.DEPOSITO -> "Depositar"
        TipoOperacion.RETIRO -> "Retirar"
        TipoOperacion.TRANSFERENCIA_ENVIADA, TipoOperacion.TRANSFERENCIA_RECIBIDA -> "Transferir"
        TipoOperacion.PAGO_SERVICIO -> "Pagar servicio"
    }

    AlertDialog(
        onDismissRequest = onCancelar,                       // Tocar fuera del diálogo = cancelar.
        title = { Text(titulo) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Campo de MONTO (siempre). Teclado decimal.
                OutlinedTextField(
                    value = monto,
                    onValueChange = { monto = it; error = null },   // Al escribir, borramos el error.
                    label = { Text("Monto ($)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                // Solo para TRANSFERIR: cuenta destino.
                if (tipo == TipoOperacion.TRANSFERENCIA_ENVIADA) {
                    OutlinedTextField(
                        value = cuentaDestino,
                        onValueChange = { cuentaDestino = it.trim(); error = null },
                        label = { Text("Cuenta destino (ej. 1002)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
                // Solo para PAGAR SERVICIOS: elegir servicio y escribir contrato.
                if (tipo == TipoOperacion.PAGO_SERVICIO) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SERVICIOS.forEach { opcion ->
                            FilterChip(
                                selected = servicio == opcion,          // Marcado si es el elegido.
                                onClick = { servicio = opcion },
                                label = { Text(opcion) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = contrato,
                        onValueChange = { contrato = it; error = null },
                        label = { Text("Código de contrato") },
                        singleLine = true
                    )
                }
                // Mensaje de error de validación, en rojo. "?.let" = solo si error no es null.
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // Validamos ANTES de enviar al servidor. Aceptamos coma o punto decimal.
                // toDoubleOrNull(): devuelve null si el texto no es un número (no lanza error).
                val valor = monto.replace(',', '.').toDoubleOrNull()
                when {
                    valor == null || valor <= 0.0 -> error = "Ingresa un monto mayor que 0"
                    tipo == TipoOperacion.TRANSFERENCIA_ENVIADA && cuentaDestino.isBlank() ->
                        error = "Ingresa la cuenta destino"
                    tipo == TipoOperacion.PAGO_SERVICIO && contrato.isBlank() ->
                        error = "Ingresa el código de contrato"
                    // Todo bien: armamos la Solicitud correcta según el tipo y la entregamos.
                    else -> onConfirmar(
                        when (tipo) {
                            TipoOperacion.DEPOSITO, TipoOperacion.TRANSFERENCIA_RECIBIDA -> Solicitud.Deposito(valor)
                            TipoOperacion.RETIRO -> Solicitud.Retiro(valor)
                            TipoOperacion.TRANSFERENCIA_ENVIADA -> Solicitud.Transferencia(cuentaDestino, valor)
                            TipoOperacion.PAGO_SERVICIO -> Solicitud.PagoServicio(servicio, contrato.trim(), valor)
                        }
                    )
                }
            }) { Text("Confirmar") }
        },
        dismissButton = {
            TextButton(onClick = onCancelar) { Text("Cancelar") }
        }
    )
}
