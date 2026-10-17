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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope          // Ámbito para lanzar corrutinas desde la UI.
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carriel_cuenca_billeteradistribuida.data.Resultado
import com.example.carriel_cuenca_billeteradistribuida.model.EstadoConexion
import com.example.carriel_cuenca_billeteradistribuida.model.Factura
import com.example.carriel_cuenca_billeteradistribuida.model.Solicitud
import com.example.carriel_cuenca_billeteradistribuida.model.TipoOperacion
import com.example.carriel_cuenca_billeteradistribuida.ui.formatearMonto
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.AmbarConectando
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.RojoEgreso
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.VerdeIngreso
import kotlinx.coroutines.launch

// Servicios básicos que se pueden pagar (deben coincidir con los del servidor).
private val SERVICIOS = listOf("LUZ", "AGUA", "INTERNET")

// EXPRESIONES REGULARES para validar lo que escribe el usuario.
private val PATRON_MONTO = Regex("""^\d+([.,]\d{1,2})?$""")      // Hasta 2 decimales: 50 | 50.5 | 50,25
private val PATRON_CUENTA = Regex("""^\d{4,10}$""")              // Entre 4 y 10 dígitos.
private val PATRON_CONTRATO = Regex("""^[A-Za-z0-9-]{4,20}$""")  // Letras, números o guion; 4 a 20.

// DIÁLOGO de una operación: valida los datos, arma una Solicitud y se la entrega a quien lo abrió.
// En PAGO DE SERVICIOS el usuario NO escribe el monto: primero CONSULTA la factura al servidor.
@Composable
fun OperacionDialog(
    tipo: TipoOperacion,
    saldo: Double?,
    estadoConexion: EstadoConexion,
    cuentaPropia: String,                                            // Para impedir transferirse a sí mismo.
    onConsultarFactura: suspend (servicio: String, codigo: String) -> Resultado<Factura>,
    onActividad: () -> Unit,                                         // Escribir aquí también es "actividad".
    onConfirmar: (Solicitud) -> Unit,
    onCancelar: () -> Unit
) {
    val esPago = tipo == TipoOperacion.PAGO_SERVICIO

    var monto by remember { mutableStateOf("") }
    var cuentaDestino by remember { mutableStateOf("") }
    var servicio by remember { mutableStateOf(SERVICIOS.first()) }
    var contrato by remember { mutableStateOf("") }
    var errorMonto by remember { mutableStateOf<String?>(null) }
    var errorExtra by remember { mutableStateOf<String?>(null) }
    // Factura consultada (null = todavía no se consultó) y si la consulta está en curso.
    var factura by remember { mutableStateOf<Factura?>(null) }
    var consultando by remember { mutableStateOf(false) }

    // Ámbito de corrutinas del diálogo: la consulta se cancela sola si el diálogo se cierra.
    val alcance = rememberCoroutineScope()

    // El cursor va al primer campo al abrir.
    val focoInicial = remember { FocusRequester() }
    LaunchedEffect(Unit) { focoInicial.requestFocus() }

    // Los diálogos son ventanas aparte (no las "ve" el detector de toques de la raíz):
    // cada cambio en los campos cuenta como actividad para no cerrar la sesión mientras se escribe.
    LaunchedEffect(monto, cuentaDestino, contrato, servicio) { onActividad() }

    val (titulo, verbo) = when (tipo) {
        TipoOperacion.DEPOSITO, TipoOperacion.TRANSFERENCIA_RECIBIDA -> "Depositar dinero" to "Depositar"
        TipoOperacion.RETIRO -> "Retirar efectivo" to "Retirar"
        TipoOperacion.TRANSFERENCIA_ENVIADA -> "Transferir a otra cuenta" to "Transferir"
        TipoOperacion.PAGO_SERVICIO -> "Pagar servicio básico" to "Pagar"
    }

    // Monto a usar: en el pago, el de la FACTURA; en lo demás, el escrito (si es válido).
    val montoValido: Double? =
        if (esPago) factura?.takeIf { !it.pagado }?.monto
        else monto.takeIf { PATRON_MONTO.matches(it) }?.replace(',', '.')?.toDoubleOrNull()

    // Consulta la factura al servidor (función suspend del ViewModel -> Repository -> Retrofit en IO).
    fun consultar() {
        val codigo = contrato.trim().uppercase()
        if (!PATRON_CONTRATO.matches(codigo)) {
            errorExtra = "Contrato de 4 a 20 letras o números"
            return
        }
        alcance.launch {
            consultando = true
            errorExtra = null
            when (val r = onConsultarFactura(servicio, codigo)) {
                is Resultado.Exito -> factura = r.datos
                is Resultado.Error -> { factura = null; errorExtra = r.mensaje }   // Ej. "El contrato no existe".
            }
            consultando = false
        }
    }

    // Valida y entrega la solicitud.
    fun confirmar() {
        if (esPago) {
            val f = factura
            errorExtra = when {
                f == null -> "Primero consulta la factura (botón de la lupa)"
                f.pagado -> "Esta factura ya fue pagada"
                else -> null
            }
            if (f == null || errorExtra != null) return
            onConfirmar(Solicitud.PagoServicio(f.servicio, f.codigo, f.monto))
            return
        }
        errorMonto = when {
            monto.isBlank() -> "Ingresa un monto"
            montoValido == null -> "Usa un número con máximo 2 decimales (ej. 25.50)"
            montoValido <= 0.0 -> "El monto debe ser mayor que 0"
            else -> null
        }
        errorExtra = if (tipo == TipoOperacion.TRANSFERENCIA_ENVIADA) when {
            !PATRON_CUENTA.matches(cuentaDestino) -> "La cuenta debe tener entre 4 y 10 dígitos"
            cuentaDestino == cuentaPropia -> "No puedes transferirte a tu propia cuenta"
            else -> null
        } else null
        val valor = montoValido ?: return
        if (errorMonto != null || errorExtra != null) return
        onConfirmar(
            when (tipo) {
                TipoOperacion.DEPOSITO, TipoOperacion.TRANSFERENCIA_RECIBIDA -> Solicitud.Deposito(valor)
                TipoOperacion.RETIRO -> Solicitud.Retiro(valor)
                TipoOperacion.TRANSFERENCIA_ENVIADA -> Solicitud.Transferencia(cuentaDestino, valor)
                TipoOperacion.PAGO_SERVICIO -> return      // (ya se manejó arriba)
            }
        )
    }

    AlertDialog(
        onDismissRequest = onCancelar,
        icon = { Icon(iconoDeTipo(tipo, servicio), contentDescription = null) },
        title = { Text(titulo) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

                // Aviso si el túnel de tiempo real no está abierto (la operación igual va por REST).
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
                        Text("Sin conexión en tiempo real: la operación se intentará igual por REST.", fontSize = 12.sp)
                    }
                }

                if (!tipo.esIngreso && saldo != null) {
                    Text("Saldo disponible: ${formatearMonto(saldo)}", color = Color.Gray, fontSize = 13.sp)
                }

                if (!esPago) {
                    // ---------- MONTO (depósito, retiro, transferencia) ----------
                    OutlinedTextField(
                        value = monto,
                        onValueChange = { monto = it.trim(); errorMonto = null },
                        label = { Text("Monto") },
                        prefix = { Text("$ ") },
                        singleLine = true,
                        isError = errorMonto != null,
                        supportingText = errorMonto?.let { { Text(it) } },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = if (tipo == TipoOperacion.TRANSFERENCIA_ENVIADA) ImeAction.Next else ImeAction.Done
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focoInicial)
                    )
                }

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

                if (esPago) {
                    // ---------- PAGO: servicio + contrato + CONSULTA de la factura ----------
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SERVICIOS.forEach { opcion ->
                            val elegido = servicio == opcion
                            FilterChip(
                                selected = elegido,
                                onClick = { servicio = opcion; factura = null; errorExtra = null },   // Cambiar servicio invalida la consulta.
                                label = { Text(opcion, fontSize = 12.sp, maxLines = 1) },
                                leadingIcon = if (elegido) {
                                    { Icon(iconoDeServicio(opcion), contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                                } else null
                            )
                        }
                    }
                    OutlinedTextField(
                        value = contrato,
                        onValueChange = { contrato = it.uppercase(); factura = null; errorExtra = null },
                        label = { Text("Código de contrato") },
                        placeholder = { Text("Ej. 445566") },
                        singleLine = true,
                        isError = errorExtra != null,
                        supportingText = errorExtra?.let { { Text(it) } },
                        // Lupa a la derecha: consulta la factura (o un círculo de carga mientras consulta).
                        trailingIcon = {
                            if (consultando) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            else IconButton(onClick = { consultar() }) { Icon(Icons.Filled.Search, contentDescription = "Consultar factura") }
                        },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Search),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focoInicial)
                    )
                    // Tarjeta con la factura consultada.
                    factura?.let { f -> TarjetaFactura(f) }
                }
            }
        },
        confirmButton = {
            Button(onClick = { confirmar() }, enabled = !consultando) {
                Text(if (montoValido != null && montoValido > 0) "$verbo ${formatearMonto(montoValido)}" else verbo)
            }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

// Muestra la factura: titular, monto y si ya está pagada.
@Composable
private fun TarjetaFactura(factura: Factura) {
    val color = if (factura.pagado) RojoEgreso else VerdeIngreso
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.10f))
            .padding(12.dp)
    ) {
        Text("${factura.servicio} · Contrato ${factura.codigo}", fontWeight = FontWeight.SemiBold)
        Text("Titular: ${factura.titular}", fontSize = 13.sp, color = Color.Gray)
        Text(
            if (factura.pagado) "Factura ya PAGADA" else "Total a pagar: ${formatearMonto(factura.monto)}",
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}
