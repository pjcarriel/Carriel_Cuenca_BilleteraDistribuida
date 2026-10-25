// Paquete de componentes reutilizables.
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
import com.example.carriel_cuenca_billeteradistribuida.ui.formatearFecha
import com.example.carriel_cuenca_billeteradistribuida.ui.formatearMonto
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.AmbarConectando
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.AzulTarjetaClaro
import com.example.carriel_cuenca_billeteradistribuida.util.cedulaValida
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.RojoEgreso
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.VerdeIngreso
import kotlinx.coroutines.launch


// EXPRESIONES REGULARES para validar lo que escribe el usuario.
// Regex("""..."""): las comillas triples evitan tener que escapar las barras "\".
// "^" = inicio del texto, "$" = fin, "\d" = un dígito, "{4,10}" = entre 4 y 10 veces.
private val PATRON_MONTO = Regex("""^\d+([.,]\d{1,2})?$""")      // Hasta 2 decimales: 50 | 50.5 | 50,25
private val PATRON_CUENTA = Regex("""^\d{4,10}$""")              // Entre 4 y 10 dígitos.
private val PATRON_CONTRATO = Regex("""^[A-Za-z0-9-]{4,20}$""")  // Letras, números o guion; 4 a 20.

// DIÁLOGO de una operación: valida los datos, arma una Solicitud y se la entrega a quien lo abrió.
// En PAGO DE SERVICIOS el usuario NO escribe el monto: primero CONSULTA la factura al servidor.
@Composable
fun OperacionDialog(
    tipo: TipoOperacion,                                        // Qué operación es (define título y campos).
    saldo: Double?,                                             // Saldo actual (null = desconocido).
    estadoConexion: EstadoConexion,                             // Para bloquear el botón sin conexión.
    cuentaPropia: String,                                            // Para impedir transferirse a sí mismo.
    servicios: List<Servicio>,                                  // Catálogo de servicios (del servidor).
    // "suspend" = función que puede ESPERAR (la respuesta del servidor) sin congelar la pantalla.
    onConsultarFactura: suspend (servicio: String, codigo: String) -> Resultado<Factura>,
    // Busca las facturas de una cédula (pago "Por cédula").
    onBuscarFacturas: suspend (cedula: String, servicio: String) -> Resultado<List<Factura>>,
    onActividad: () -> Unit,                                         // Escribir aquí también es "actividad".
    onConfirmar: (Solicitud) -> Unit,                           // Se llama con la Solicitud ya validada.
    onCancelar: () -> Unit                                      // Se llama al cerrar sin hacer nada.
) {
    val esPago = tipo == TipoOperacion.PAGO_SERVICIO            // true = pago de servicio (cambian los campos).

    // ESTADO DEL DIÁLOGO: "remember { mutableStateOf(...) }" guarda un valor que SOBREVIVE a cada
    // redibujado (recomposición); al cambiarlo, Compose vuelve a dibujar lo que lo usa.
    // "by" permite leerlo y escribirlo como una variable normal (ej. monto = "10").
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

    // Ámbito de corrutinas del diálogo: la consulta se cancela sola si el diálogo se cierra.
    val alcance = rememberCoroutineScope()

    // El cursor va al primer campo al abrir.
    // FocusRequester: permite pedir el foco (cursor + teclado) para un campo concreto.
    val focoInicial = remember { FocusRequester() }
    LaunchedEffect(Unit) { focoInicial.requestFocus() }         // LaunchedEffect(Unit): corre UNA vez al aparecer.

    // Los diálogos son ventanas aparte (no las "ve" el detector de toques de la raíz):
    // cada cambio en los campos cuenta como actividad para no cerrar la sesión mientras se escribe.
    // LaunchedEffect(claves): se vuelve a ejecutar cada vez que cambia ALGUNA de esas variables.
    LaunchedEffect(monto, cuentaDestino, contrato, servicio) { onActividad() }

    // Título del diálogo y verbo del botón según el tipo ("to" arma un par; "val (a, b)" lo separa).
    // TRANSFERENCIA_RECIBIDA no tiene botón propio: va con depósito solo para cubrir todos los casos del enum.
    val (titulo, verbo) = when (tipo) {
        TipoOperacion.DEPOSITO, TipoOperacion.TRANSFERENCIA_RECIBIDA -> "Depositar dinero" to "Depositar"
        TipoOperacion.RETIRO -> "Retirar efectivo" to "Retirar"
        TipoOperacion.TRANSFERENCIA_ENVIADA -> "Transferir a otra cuenta" to "Transferir"
        TipoOperacion.PAGO_SERVICIO -> "Pagar servicio básico" to "Pagar"
    }

    // Monto a usar: en el pago, el de la FACTURA; en lo demás, el escrito (si es válido).
    // takeIf { condición }: devuelve el valor si se cumple, o null si no. "?." sigue solo si no es null.
    // Pago: la factura si existe y NO está pagada. Otros: el texto si cumple el patrón (con "," -> ".").
    val montoValido: Double? =
        if (esPago) factura?.takeIf { !it.pagado }?.monto
        else monto.takeIf { PATRON_MONTO.matches(it) }?.replace(',', '.')?.toDoubleOrNull()

    // Consulta la factura al servidor (función suspend del ViewModel -> Repository -> Retrofit en IO).
    // Función LOCAL (definida dentro del @Composable): puede leer y cambiar el estado de arriba.
    fun consultar() {
        val codigo = contrato.trim().uppercase()                // Sin espacios y en mayúsculas.
        // Si el código no cumple el patrón, se muestra el error y NO se consulta ("return" sale de la función).
        if (!PATRON_CONTRATO.matches(codigo)) {
            errorExtra = "Contrato de 4 a 20 letras o números"
            return
        }
        // launch: inicia una CORRUTINA; la pantalla sigue respondiendo mientras se espera al servidor.
        alcance.launch {
            consultando = true                                  // Muestra el círculo de carga.
            errorExtra = null                                   // Borra errores anteriores.
            // "when (val r = ...)": guarda el resultado en r y revisa si fue Exito o Error (Resultado es una sealed class).
            when (val r = onConsultarFactura(servicio, codigo)) {
                is Resultado.Exito -> factura = r.datos         // Guardamos la factura recibida.
                is Resultado.Error -> { factura = null; errorExtra = r.mensaje }   // Ej. "El contrato no existe".
            }
            consultando = false                                 // Oculta el círculo de carga.
        }
    }

    // Busca las facturas de la cédula escrita para el servicio elegido.
    fun buscarPorCedula() {
        // Se valida AQUÍ con el algoritmo de la cédula ecuatoriana (util/Cedula.kt), sin ir al servidor.
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

    // BÚSQUEDA AUTOMÁTICA: en cuanto la cédula tiene 10 dígitos (o se cambia de servicio con la cédula
    // ya escrita), se buscan las facturas sin tener que tocar la lupa.
    LaunchedEffect(porCedula, cedula, servicio) {
        if (porCedula && cedula.length == 10) buscarPorCedula()
    }

    // Valida y entrega la solicitud.
    fun confirmar() {
        // PAGO: solo se confirma si ya se consultó la factura y no está pagada.
        if (esPago) {
            val f = factura                                     // Copia local: así Kotlin puede hacer "smart cast" (factura es un var).
            // "when" sin argumento: el primer caso verdadero da el mensaje; null = todo bien.
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
        val valor = montoValido ?: return                       // "?: return": si el monto es null, sale de la función.
        if (errorMonto != null || errorExtra != null) return    // Si hay algún error, no se envía nada.
        // Arma la Solicitud correcta según el tipo y se la entrega a quien abrió el diálogo.
        onConfirmar(
            when (tipo) {
                TipoOperacion.DEPOSITO, TipoOperacion.TRANSFERENCIA_RECIBIDA -> Solicitud.Deposito(valor)
                TipoOperacion.RETIRO -> Solicitud.Retiro(valor)
                TipoOperacion.TRANSFERENCIA_ENVIADA -> Solicitud.Transferencia(cuentaDestino, valor)
                TipoOperacion.PAGO_SERVICIO -> return      // (ya se manejó arriba)
            }
        )
    }

    // AlertDialog de Material 3: ventana flotante con ícono, título, contenido y botones.
    AlertDialog(
        onDismissRequest = onCancelar,                          // Tocar fuera o "atrás" = cancelar.
        icon = { Icon(iconoDeTipo(tipo, servicio), contentDescription = null) },  // Ícono según el tipo o el servicio.
        title = { Text(titulo) },                               // Título del diálogo.
        text = {                                                // Cuerpo del diálogo: los campos.
            // verticalScroll: con la lista de facturas el contenido puede no caber; así se desplaza.
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),       // Campos separados 10 dp.
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {

                // Aviso si se perdió la conexión con el diálogo abierto: el botón queda bloqueado.
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
                        supportingText = errorMonto?.let { { Text(it) } },  // Mensaje de error bajo el campo (o nada).
                        // Teclado numérico con decimales; la tecla de acción es "Siguiente" si falta la cuenta, si no "Listo".
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = if (tipo == TipoOperacion.TRANSFERENCIA_ENVIADA) ImeAction.Next else ImeAction.Done
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focoInicial)        // Recibe el foco al abrir el diálogo.
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
                    // ---------- PAGO: servicio + contrato (o cédula) + CONSULTA de la factura ----------
                    // Una ficha (FilterChip) por servicio; la elegida queda marcada y muestra su ícono.
                    // Los servicios vienen del CATÁLOGO del servidor; horizontalScroll: si son muchos, se deslizan.
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        servicios.map { it.codigo }.forEach { opcion ->
                            val elegido = servicio == opcion    // ¿Es el servicio seleccionado?
                            FilterChip(
                                selected = elegido,
                                // Cambiar servicio invalida la consulta (y la lista de la cédula).
                                onClick = { servicio = opcion; factura = null; facturasCedula = null; errorExtra = null },
                                label = { Text(opcion, fontSize = 12.sp, maxLines = 1) },
                                // leadingIcon: ícono a la izquierda solo en la ficha elegida (null = sin ícono).
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
                            // Solo dígitos y como máximo 10 (filter + take).
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
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } },  // Botón secundario.
        containerColor = MaterialTheme.colorScheme.surface      // Fondo del diálogo según el tema.
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

// Muestra la factura: titular, monto y si ya está pagada.
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
