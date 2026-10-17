// Paquete de componentes reutilizables.
package com.example.carriel_cuenca_billeteradistribuida.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester                 // Permite poner el cursor en un campo por código.
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction                 // Botón de acción del teclado ("Siguiente"/"Listo").
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carriel_cuenca_billeteradistribuida.data.remote.ConfigServidor
import com.example.carriel_cuenca_billeteradistribuida.model.EstadoConexion
import com.example.carriel_cuenca_billeteradistribuida.model.Solicitud
import com.example.carriel_cuenca_billeteradistribuida.model.TipoOperacion
import com.example.carriel_cuenca_billeteradistribuida.ui.formatearMonto
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.AmbarConectando

// Servicios básicos que se pueden pagar (deben coincidir con los del servidor).
private val SERVICIOS = listOf("LUZ", "AGUA", "INTERNET")

// EXPRESIONES REGULARES (patrones de texto) para validar lo que escribe el usuario:
private val PATRON_MONTO = Regex("""^\d+([.,]\d{1,2})?$""")      // Número con hasta 2 decimales: 50 | 50.5 | 50,25
private val PATRON_CUENTA = Regex("""^\d{4,10}$""")              // Entre 4 y 10 dígitos.
private val PATRON_CONTRATO = Regex("""^[A-Za-z0-9-]{4,20}$""")  // Letras, números o guion; 4 a 20 caracteres.

// DIÁLOGO de una operación. No llama al servidor: VALIDA los datos, arma una Solicitud
// y se la entrega a quien lo abrió mediante la lambda onConfirmar.
@Composable
fun OperacionDialog(
    tipo: TipoOperacion,                     // Qué botón se tocó.
    saldo: Double?,                          // Saldo actual (para mostrar "Saldo disponible").
    estadoConexion: EstadoConexion,          // Para avisar si no hay conexión en tiempo real.
    onConfirmar: (Solicitud) -> Unit,
    onCancelar: () -> Unit
) {
    // Estado de cada campo: sobrevive a los redibujados y, al cambiar, redibuja el diálogo.
    var monto by remember { mutableStateOf("") }
    var cuentaDestino by remember { mutableStateOf("") }
    var servicio by remember { mutableStateOf(SERVICIOS.first()) }
    var contrato by remember { mutableStateOf("") }
    // Mensaje de error de validación de cada campo (null = sin error).
    var errorMonto by remember { mutableStateOf<String?>(null) }
    var errorExtra by remember { mutableStateOf<String?>(null) }   // Error de cuenta destino o contrato.

    // FocusRequester: al abrir el diálogo, el cursor va directo al campo de monto (y se abre el teclado).
    val focoMonto = remember { FocusRequester() }
    LaunchedEffect(Unit) { focoMonto.requestFocus() }

    // Título e ícono según el tipo ("when" como expresión que devuelve un Pair).
    val (titulo, verbo) = when (tipo) {
        TipoOperacion.DEPOSITO, TipoOperacion.TRANSFERENCIA_RECIBIDA -> "Depositar dinero" to "Depositar"
        TipoOperacion.RETIRO -> "Retirar efectivo" to "Retirar"
        TipoOperacion.TRANSFERENCIA_ENVIADA -> "Transferir a otra cuenta" to "Transferir"
        TipoOperacion.PAGO_SERVICIO -> "Pagar servicio básico" to "Pagar"
    }
    // El dinero sale de la cuenta en todo menos en el depósito: ahí mostramos el saldo disponible.
    val saleDinero = !tipo.esIngreso

    // Monto ya convertido a número (null si el texto aún no es válido); se usa en el botón.
    val montoValido = monto.takeIf { PATRON_MONTO.matches(it) }?.replace(',', '.')?.toDoubleOrNull()

    // Valida todo y, si está bien, entrega la solicitud. Es una función LOCAL (dentro de otra función).
    fun confirmar() {
        errorMonto = when {
            monto.isBlank() -> "Ingresa un monto"
            montoValido == null -> "Usa un número con máximo 2 decimales (ej. 25.50)"
            montoValido <= 0.0 -> "El monto debe ser mayor que 0"
            else -> null
        }
        errorExtra = when (tipo) {
            TipoOperacion.TRANSFERENCIA_ENVIADA -> when {
                !PATRON_CUENTA.matches(cuentaDestino) -> "La cuenta debe tener entre 4 y 10 dígitos"
                cuentaDestino == ConfigServidor.CUENTA_APP -> "No puedes transferirte a tu propia cuenta"
                else -> null
            }
            TipoOperacion.PAGO_SERVICIO ->
                if (!PATRON_CONTRATO.matches(contrato.trim())) "Contrato de 4 a 20 letras o números" else null
            else -> null
        }
        // Solo si NO hay errores armamos la Solicitud y la entregamos.
        // "?: return" = si montoValido fuera null, salimos de la función.
        val valor = montoValido ?: return
        if (errorMonto != null || errorExtra != null) return
        onConfirmar(
            when (tipo) {
                TipoOperacion.DEPOSITO, TipoOperacion.TRANSFERENCIA_RECIBIDA -> Solicitud.Deposito(valor)
                TipoOperacion.RETIRO -> Solicitud.Retiro(valor)
                TipoOperacion.TRANSFERENCIA_ENVIADA -> Solicitud.Transferencia(cuentaDestino, valor)
                TipoOperacion.PAGO_SERVICIO -> Solicitud.PagoServicio(servicio, contrato.trim().uppercase(), valor)
            }
        )
    }

    AlertDialog(
        onDismissRequest = onCancelar,
        // Ícono grande arriba del título (el mismo del botón que se tocó).
        icon = {
            Icon(iconoDeTipo(tipo, servicio), contentDescription = null)
        },
        title = { Text(titulo) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

                // AVISO si el túnel de tiempo real no está abierto (la operación igual va por REST).
                if (estadoConexion != EstadoConexion.EN_LINEA) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(AmbarConectando.copy(alpha = 0.15f))
                            .padding(10.dp)
                    ) {
                        Icon(Icons.Filled.WifiOff, contentDescription = null, tint = AmbarConectando, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Sin conexión en tiempo real: la operación se intentará igual por REST.",
                            fontSize = 12.sp
                        )
                    }
                }

                // Saldo disponible (solo cuando el dinero sale de la cuenta).
                if (saleDinero && saldo != null) {
                    Text("Saldo disponible: ${formatearMonto(saldo)}", color = Color.Gray, fontSize = 13.sp)
                }

                // Campo MONTO: prefijo "$", teclado decimal, error debajo en rojo.
                OutlinedTextField(
                    value = monto,
                    onValueChange = { monto = it.trim(); errorMonto = null },
                    label = { Text("Monto") },
                    prefix = { Text("$ ") },
                    singleLine = true,
                    isError = errorMonto != null,                          // Borde rojo si hay error.
                    supportingText = errorMonto?.let { { Text(it) } },      // Mensaje debajo (o nada).
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        // Si hay otro campo después, el teclado muestra "Siguiente"; si no, "Listo".
                        imeAction = if (tipo == TipoOperacion.DEPOSITO || tipo == TipoOperacion.RETIRO) ImeAction.Done else ImeAction.Next
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focoMonto)
                )

                // Solo para TRANSFERIR: cuenta destino.
                if (tipo == TipoOperacion.TRANSFERENCIA_ENVIADA) {
                    OutlinedTextField(
                        value = cuentaDestino,
                        onValueChange = { cuentaDestino = it.trim(); errorExtra = null },
                        label = { Text("Cuenta destino") },
                        placeholder = { Text("Ej. 1002") },
                        singleLine = true,
                        isError = errorExtra != null,
                        supportingText = errorExtra?.let { { Text(it) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Solo para PAGAR SERVICIOS: elegir servicio (con ícono) y escribir el contrato.
                if (tipo == TipoOperacion.PAGO_SERVICIO) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SERVICIOS.forEach { opcion ->
                            val elegido = servicio == opcion
                            FilterChip(
                                selected = elegido,
                                onClick = { servicio = opcion },
                                // maxLines = 1: el nombre nunca se parte en dos líneas.
                                label = { Text(opcion, fontSize = 12.sp, maxLines = 1) },
                                // El ícono solo se muestra en el chip ELEGIDO: así los tres caben en una fila.
                                // "if" como expresión: si no está elegido, leadingIcon = null (sin ícono).
                                leadingIcon = if (elegido) {
                                    {
                                        Icon(iconoDeServicio(opcion), contentDescription = null,
                                            modifier = Modifier.size(FilterChipDefaults.IconSize))
                                    }
                                } else null
                            )
                        }
                    }
                    OutlinedTextField(
                        value = contrato,
                        onValueChange = { contrato = it; errorExtra = null },
                        label = { Text("Código de contrato") },
                        placeholder = { Text("Ej. 445566") },
                        singleLine = true,
                        isError = errorExtra != null,
                        supportingText = errorExtra?.let { { Text(it) } },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        // Botón principal RELLENO que dice exactamente qué hará: "Retirar $ 20.00".
        confirmButton = {
            Button(onClick = { confirmar() }) {
                Text(if (montoValido != null && montoValido > 0) "$verbo ${formatearMonto(montoValido)}" else verbo)
            }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } },
        containerColor = MaterialTheme.colorScheme.surface
    )
}
