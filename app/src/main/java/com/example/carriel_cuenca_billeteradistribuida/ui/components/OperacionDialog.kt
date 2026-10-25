// =====================================================================
//   La ventana donde el usuario escribe los datos de una operación
//   (monto, cuenta, contrato o cédula). La app los revisa y arma el pedido.
// =====================================================================

package com.example.carriel_cuenca_billeteradistribuida.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.example.carriel_cuenca_billeteradistribuida.model.Servicio
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.carriel_cuenca_billeteradistribuida.ui.formatearFecha
import com.example.carriel_cuenca_billeteradistribuida.ui.formatearMonto
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.AmbarConectando
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.AzulTarjetaClaro
import com.example.carriel_cuenca_billeteradistribuida.util.cedulaValida
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.RojoEgreso
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.VerdeIngreso
import kotlinx.coroutines.launch


// REGLAS DE FORMATO (expresiones regulares): sirven para revisar si lo que
// escribió el usuario tiene la forma correcta antes de enviarlo.
private val PATRON_MONTO = Regex("""^\d+([.,]\d{1,2})?$""")      // Hasta 2 decimales: 50 | 50.5 | 50,25
private val PATRON_CUENTA = Regex("""^\d{4,10}$""")              // Entre 4 y 10 dígitos.
private val PATRON_CONTRATO = Regex("""^[A-Za-z0-9-]{4,20}$""")  // Letras, números o guion; 4 a 20.

// Ventana de una operación: revisa los datos y arma el pedido (Solicitud).
// En el pago de servicios, primero se busca la factura en el servidor.
@Composable
fun OperacionDialog(
    tipo: TipoOperacion,                                        // Qué operación es (define título y campos).
    saldo: Double?,                                             // Saldo actual (null = desconocido).
    estadoConexion: EstadoConexion,                             // Para bloquear el botón sin conexión.
    cuentaPropia: String,                                            // Para impedir transferirse a sí mismo.
    servicios: List<Servicio>,                                  // Catálogo de servicios (del servidor).
    // Busca una factura por código de contrato. ("suspend" = puede esperar al servidor sin congelar la pantalla.)
    onConsultarFactura: suspend (servicio: String, codigo: String) -> Resultado<Factura>,
    // Busca las facturas de una cédula (pago "Por cédula").
    onBuscarFacturas: suspend (cedula: String, servicio: String) -> Resultado<List<Factura>>,
    onActividad: () -> Unit,                                         // Avisa que el usuario está escribiendo (no está inactivo).
    onConfirmar: (Solicitud) -> Unit,                           // Se llama con el pedido ya revisado.
    onCancelar: () -> Unit                                      // Se llama al cerrar sin hacer nada.
) {
    val esPago = tipo == TipoOperacion.PAGO_SERVICIO            // true = pago de servicio (cambian los campos).

    // DATOS DE LA VENTANA: lo que el usuario va escribiendo y los mensajes de error.
    // La ventana "recuerda" estos valores y se actualiza sola cuando cambian.
    var monto by remember { mutableStateOf("") }                // Texto del campo Monto.
    var cuentaDestino by remember { mutableStateOf("") }        // Cuenta a la que se transfiere.
    var servicio by remember { mutableStateOf(servicios.firstOrNull()?.codigo ?: "LUZ") }  // Servicio elegido (el primero).
    var contrato by remember { mutableStateOf("") }             // Código de contrato escrito.
    var errorMonto by remember { mutableStateOf<String?>(null) }  // Error del monto (null = sin error).
    var errorExtra by remember { mutableStateOf<String?>(null) }  // Error de la cuenta destino o del contrato.
    // Factura consultada (null = todavía no se consultó) y si la consulta está en curso.
    var factura by remember { mutableStateOf<Factura?>(null) }
    var consultando by remember { mutableStateOf(false) }
    // PAGO POR CÉDULA: el modo elegido, la cédula escrita y las facturas encontradas (null = no se buscó).
    var porCedula by remember { mutableStateOf(false) }
    var cedula by remember { mutableStateOf("") }
    var facturasCedula by remember { mutableStateOf<List<Factura>?>(null) }

    // Permite hacer consultas al servidor en segundo plano.
    // Si la ventana se cierra, la consulta se cancela sola.
    val alcance = rememberCoroutineScope()

    // Al abrir la ventana, el cursor y el teclado aparecen en el primer campo.
    val focoInicial = remember { FocusRequester() }
    LaunchedEffect(Unit) { focoInicial.requestFocus() }         // Se ejecuta una sola vez, al abrir.

    // Cada vez que el usuario escribe algo, se avisa que hay actividad,
    // para que la sesión no se cierre por inactividad mientras llena los datos.
    LaunchedEffect(monto, cuentaDestino, contrato, servicio) { onActividad() }

    // Título de la ventana y texto del botón según la operación.
    // (TRANSFERENCIA_RECIBIDA no tiene botón propio; se pone junto a depósito solo para cubrir todos los casos.)
    val (titulo, verbo) = when (tipo) {
        TipoOperacion.DEPOSITO, TipoOperacion.TRANSFERENCIA_RECIBIDA -> "Depositar dinero" to "Depositar"
        TipoOperacion.RETIRO -> "Retirar efectivo" to "Retirar"
        TipoOperacion.TRANSFERENCIA_ENVIADA -> "Transferir a otra cuenta" to "Transferir"
        TipoOperacion.PAGO_SERVICIO -> "Pagar servicio básico" to "Pagar"
    }

    // Monto a usar: en el pago, el de la factura; en lo demás, el que escribió el usuario.
    val montoValido: Double? =
        if (esPago) factura?.takeIf { !it.pagado }?.monto
        else monto.takeIf { PATRON_MONTO.matches(it) }?.replace(',', '.')?.toDoubleOrNull()

    // Pide al servidor la factura del código de contrato escrito.
    fun consultar() {
        val codigo = contrato.trim().uppercase()                // Sin espacios y en mayúsculas.
        // Si el código no tiene el formato correcto, se muestra el error y no se consulta.
        if (!PATRON_CONTRATO.matches(codigo)) {
            errorExtra = "Contrato de 4 a 20 letras o números"
            return
        }
        // Se consulta en segundo plano: la pantalla sigue respondiendo mientras se espera al servidor.
        alcance.launch {
            consultando = true                                  // Muestra el círculo de carga.
            errorExtra = null                                   // Borra errores anteriores.
            // Revisamos si la respuesta fue un éxito (llegó la factura) o un error.
            when (val r = onConsultarFactura(servicio, codigo)) {
                is Resultado.Exito -> factura = r.datos         // Guardamos la factura recibida.
                is Resultado.Error -> { factura = null; errorExtra = r.mensaje }   // Ej. "El contrato no existe".
            }
            consultando = false                                 // Oculta el círculo de carga.
        }
    }

    // Busca en el servidor las facturas de la cédula escrita, para el servicio elegido.
    fun buscarPorCedula() {
        // Primero se revisa en el teléfono que la cédula ecuatoriana sea real (util/Cedula.kt).
        if (!cedulaValida(cedula)) {
            errorExtra = "Cédula no válida (10 dígitos con dígito verificador correcto)"
            return
        }
        alcance.launch {
            consultando = true
            errorExtra = null
            factura = null                                      // Se elige de nuevo de la lista.
            when (val r = onBuscarFacturas(cedula, servicio)) {
                is Resultado.Exito -> {
                    facturasCedula = r.datos
                    if (r.datos.isEmpty()) errorExtra = "Esta cédula no tiene facturas de $servicio"
                }
                is Resultado.Error -> { facturasCedula = null; errorExtra = r.mensaje }
            }
            consultando = false
        }
    }

    // BÚSQUEDA AUTOMÁTICA: apenas la cédula tiene 10 dígitos (o se cambia de servicio con la
    // cédula ya escrita), se buscan las facturas sin tener que tocar la lupa.
    LaunchedEffect(porCedula, cedula, servicio) {
        if (porCedula && cedula.length == 10) buscarPorCedula()
    }

    // Se ejecuta al tocar el botón principal: revisa todo y, si está bien, envía el pedido.
    fun confirmar() {
        // PAGO: solo se confirma si ya se consultó la factura y no está pagada.
        if (esPago) {
            val f = factura                                     // Copia de la factura para revisarla.
            // Se busca el primer problema que haya; si no hay ninguno, queda null (todo bien).
            errorExtra = when {
                f == null && porCedula -> "Toca una de las facturas PENDIENTES de la lista para elegirla"
                f == null -> "Primero consulta la factura (botón de la lupa)"
                f.pagado -> "Esta factura ya fue pagada"
                else -> null
            }
            if (f == null || errorExtra != null) return         // Hay error: no se envía nada.
            onConfirmar(Solicitud.PagoServicio(f.servicio, f.codigo, f.monto))  // Se paga el monto de la FACTURA.
            return                                              // Termina aquí; lo de abajo es para las demás operaciones.
        }
        // DEPÓSITO / RETIRO / TRANSFERENCIA: se valida el monto escrito.
        errorMonto = when {
            monto.isBlank() -> "Ingresa un monto"
            montoValido == null -> "Usa un número con máximo 2 decimales (ej. 25.50)"
            montoValido <= 0.0 -> "El monto debe ser mayor que 0"
            else -> null
        }
        // Solo en transferencias se valida la cuenta destino; en lo demás no hay error extra.
        errorExtra = if (tipo == TipoOperacion.TRANSFERENCIA_ENVIADA) when {
            !PATRON_CUENTA.matches(cuentaDestino) -> "La cuenta debe tener entre 4 y 10 dígitos"
            cuentaDestino == cuentaPropia -> "No puedes transferirte a tu propia cuenta"
            else -> null
        } else null
        val valor = montoValido ?: return                       // Si no hay monto válido, no se sigue.
        if (errorMonto != null || errorExtra != null) return    // Si hay algún error, no se envía nada.
        // Arma el pedido correcto según la operación y se lo pasa a la pantalla.
        onConfirmar(
            when (tipo) {
                TipoOperacion.DEPOSITO, TipoOperacion.TRANSFERENCIA_RECIBIDA -> Solicitud.Deposito(valor)
                TipoOperacion.RETIRO -> Solicitud.Retiro(valor)
                TipoOperacion.TRANSFERENCIA_ENVIADA -> Solicitud.Transferencia(cuentaDestino, valor)
                TipoOperacion.PAGO_SERVICIO -> return      // (ya se manejó arriba)
            }
        )
    }

    // La ventana flotante: ícono, título, campos y botones.
    AlertDialog(
        onDismissRequest = onCancelar,                          // Tocar fuera o "atrás" = cancelar.
        icon = { Icon(iconoDeTipo(tipo, servicio), contentDescription = null) },  // Ícono según el tipo o el servicio.
        title = { Text(titulo) },                               // Título de la ventana.
        text = {                                                // Cuerpo de la ventana: los campos.
            // Si el contenido no cabe (por ejemplo, con muchas facturas), se puede desplazar.
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),       // Campos separados 10 dp.
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {

                // Aviso si se perdió la conexión con la ventana abierta: el botón queda bloqueado.
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
                        Text("Sin conexión con el servidor: espera a que vuelva para confirmar.", fontSize = 12.sp)
                    }
                }

                // En retiros, transferencias y pagos se muestra el saldo para que el usuario sepa cuánto tiene.
                if (!tipo.esIngreso && saldo != null) {
                    Text("Saldo disponible: ${formatearMonto(saldo)}", color = Color.Gray, fontSize = 13.sp)
                }

                if (!esPago) {
                    // ---------- MONTO (depósito, retiro, transferencia) ----------
                    OutlinedTextField(
                        value = monto,
                        onValueChange = { monto = it.trim(); errorMonto = null },  // Al escribir se borra el error.
                        label = { Text("Monto") },
                        prefix = { Text("$ ") },                // Texto fijo antes del número.
                        singleLine = true,                      // Una sola línea.
                        isError = errorMonto != null,           // Pinta el campo en rojo si hay error.
                        supportingText = errorMonto?.let { { Text(it) } },  // Mensaje de error bajo el campo (si hay).
                        // Teclado de números con decimales.
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = if (tipo == TipoOperacion.TRANSFERENCIA_ENVIADA) ImeAction.Next else ImeAction.Done
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focoInicial)        // Aquí aparece el cursor al abrir.
                    )
                }

                // ---------- CUENTA DESTINO (solo transferencias) ----------
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
                    // ---------- PAGO: ¿por código de contrato o por cédula? ----------
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = !porCedula,
                            onClick = { porCedula = false; factura = null; errorExtra = null },
                            label = { Text("Por contrato") }
                        )
                        FilterChip(
                            selected = porCedula,
                            onClick = { porCedula = true; factura = null; errorExtra = null },
                            label = { Text("Por cédula") }
                        )
                    }
                    // ---------- PAGO: elegir servicio y buscar la factura ----------
                    // Una ficha por servicio; la elegida muestra su ícono.
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        servicios.map { it.codigo }.forEach { opcion ->
                            val elegido = servicio == opcion    // ¿Es el servicio seleccionado?
                            FilterChip(
                                selected = elegido,
                                // Al cambiar de servicio se borra la factura y la lista anteriores.
                                onClick = { servicio = opcion; factura = null; facturasCedula = null; errorExtra = null },
                                label = { Text(opcion, fontSize = 12.sp, maxLines = 1) },
                                // Solo la ficha elegida muestra el ícono a la izquierda.
                                leadingIcon = if (elegido) {
                                    { Icon(iconoDeServicio(opcion), contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                                } else null
                            )
                        }
                    }
                    if (porCedula) {
                        // ---------- POR CÉDULA: escribir la cédula y buscar ----------
                        OutlinedTextField(
                            value = cedula,
                            // Solo acepta números y como máximo 10.
                            onValueChange = { cedula = it.filter { c -> c.isDigit() }.take(10); facturasCedula = null; factura = null; errorExtra = null },
                            label = { Text("Cédula del titular") },
                            placeholder = { Text("Ej. 1712345675") },
                            singleLine = true,
                            isError = errorExtra != null,
                            supportingText = errorExtra?.let { { Text(it) } },
                            trailingIcon = {
                                if (consultando) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                else IconButton(onClick = { buscarPorCedula() }) { Icon(Icons.Filled.Search, contentDescription = "Buscar facturas") }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Search),
                            // La tecla "Buscar" del teclado también busca.
                            keyboardActions = KeyboardActions(onSearch = { buscarPorCedula() }),
                            modifier = Modifier.fillMaxWidth()
                        )
                        // Lista de facturas de esa cédula: las pendientes se pueden TOCAR para elegirlas.
                        facturasCedula?.forEach { f ->
                            FilaFacturaCedula(f, elegida = factura?.codigo == f.codigo, onElegir = { factura = f; errorExtra = null })
                        }
                    } else OutlinedTextField(
                        value = contrato,
                        // Al cambiar el contrato se borra la factura anterior (hay que volver a consultar).
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
                        // Teclado en MAYÚSCULAS y tecla de acción "Buscar".
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Search),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focoInicial)
                    )
                    // Tarjeta con la factura consultada (en el modo cédula ya se ve resaltada en la lista).
                    if (!porCedula) factura?.let { f -> TarjetaFactura(f) }
                }
            }
        },
        // Botón principal: se bloquea mientras se consulta o si no hay conexión.
        confirmButton = {
            Button(onClick = { confirmar() }, enabled = !consultando && estadoConexion == EstadoConexion.EN_LINEA) {
                // Si ya hay un monto válido, el botón lo muestra (ej. "Depositar $ 25.00").
                Text(if (montoValido != null && montoValido > 0) "$verbo ${formatearMonto(montoValido)}" else verbo)
            }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } },  // Botón para cerrar sin hacer nada.
        containerColor = MaterialTheme.colorScheme.surface      // Color de fondo de la ventana.
    )
}

// Una factura de la búsqueda por cédula: mes, código, monto y estado (PENDIENTE en rojo / PAGADA en verde).
// Solo las pendientes se pueden tocar; la elegida se resalta con un borde azul.
@Composable
private fun FilaFacturaCedula(factura: Factura, elegida: Boolean, onElegir: () -> Unit) {
    val colorEstado = if (factura.pagado) VerdeIngreso else RojoEgreso
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (elegida) AzulTarjetaClaro.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant)
            .border(2.dp, if (elegida) AzulTarjetaClaro else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable(enabled = !factura.pagado, onClick = onElegir)    // Una pagada no se puede elegir.
            .padding(10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(factura.periodo ?: "Sin periodo", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text("Contrato ${factura.codigo} · ${factura.titular}", fontSize = 12.sp, color = Color.Gray)
            // Si está pagada: cuándo y desde qué cuenta.
            if (factura.pagado) {
                Text(
                    "Pagada" + (factura.fechaPago?.let { " el ${formatearFecha(it)}" } ?: "") +
                        (factura.pagadoPor?.let { " (cuenta $it)" } ?: ""),
                    fontSize = 11.sp, color = Color.Gray
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatearMonto(factura.monto), fontWeight = FontWeight.Bold)
            Text(if (factura.pagado) "PAGADA" else "PENDIENTE", color = colorEstado, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// Tarjeta con la factura encontrada por código de contrato: titular, monto y si ya está pagada.
@Composable
private fun TarjetaFactura(factura: Factura) {
    val color = if (factura.pagado) RojoEgreso else VerdeIngreso  // Rojo si ya está pagada; verde si se puede pagar.
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
