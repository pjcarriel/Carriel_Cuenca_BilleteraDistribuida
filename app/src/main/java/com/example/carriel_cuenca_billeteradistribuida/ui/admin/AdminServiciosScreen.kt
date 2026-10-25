// =====================================================================
//   La pantalla "Servicios y facturas": aquí la empresa emite facturas,
//   ve quién ya pagó y agrega servicios nuevos (ej. gas).
// =====================================================================

package com.example.carriel_cuenca_billeteradistribuida.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.carriel_cuenca_billeteradistribuida.data.Resultado
import com.example.carriel_cuenca_billeteradistribuida.model.Factura
import com.example.carriel_cuenca_billeteradistribuida.model.Servicio
import com.example.carriel_cuenca_billeteradistribuida.ui.BilleteraViewModel
import com.example.carriel_cuenca_billeteradistribuida.ui.components.CLAVES_ICONO
import com.example.carriel_cuenca_billeteradistribuida.ui.components.iconoDeServicio
import com.example.carriel_cuenca_billeteradistribuida.ui.components.iconoPorClave
import com.example.carriel_cuenca_billeteradistribuida.ui.formatearFecha
import com.example.carriel_cuenca_billeteradistribuida.ui.formatearMonto
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.AzulTarjeta
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.AzulTarjetaClaro
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.RojoEgreso
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.VerdeIngreso
import com.example.carriel_cuenca_billeteradistribuida.util.cedulaValida
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

// Pantalla "Servicios y facturas" con dos pestañas:
// FACTURAS (emitir y ver pagos en vivo) y SERVICIOS (agregar servicios nuevos).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminServiciosScreen(
    viewModel: BilleteraViewModel,      // El mismo "cerebro" de la pantalla de inicio (misma sesión y conexión).
    onVolver: () -> Unit,
    onActividad: () -> Unit             // Avisa que el usuario está usando la app (para que no se cierre la sesión).
) {
    // Datos que vienen del ViewModel; la pantalla se actualiza sola cuando cambian.
    val admin by viewModel.admin.collectAsStateWithLifecycle()
    val servicios by viewModel.servicios.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }             // Mensajitos que aparecen abajo.
    val alcance = rememberCoroutineScope()                      // Para tareas en segundo plano.
    var pestana by remember { mutableIntStateOf(0) }            // 0 = Facturas, 1 = Servicios.
    var mostrarNuevaFactura by remember { mutableStateOf(false) }
    var mostrarNuevoServicio by remember { mutableStateOf(false) }
    // Cada vez que cambia la lista de facturas, se vuelve arriba (ahí están las pendientes).
    val estadoLista = rememberLazyListState()
    LaunchedEffect(admin.facturas) { estadoLista.scrollToItem(0) }

    // Al abrir la pantalla se cargan las facturas; al salir, se avisa al ViewModel para que deje de recargarlas.
    LaunchedEffect(Unit) { viewModel.cargarFacturasAdmin() }
    DisposableEffect(Unit) { onDispose { viewModel.cerrarAdmin() } }
    // Avisos en tiempo real (alguien pagó o se emitió una factura): se muestran como mensajito abajo.
    LaunchedEffect(Unit) {
        viewModel.avisosFacturas.collect { detalle -> onActividad(); snackbar.showSnackbar("🔔 $detalle") }
    }

    // Facturas separadas en pendientes y pagadas (para el resumen).
    val pendientes = admin.facturas.filter { !it.pagado }
    val pagadas = admin.facturas.filter { it.pagado }

    // Estructura de la pantalla: barra superior, botón flotante y contenido.
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Servicios y facturas") },
                navigationIcon = { IconButton(onClick = onVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") } },
                actions = {
                    IconButton(onClick = { onActividad(); viewModel.cargarFacturasAdmin(); viewModel.cargarServicios() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Recargar")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        // El botón flotante cambia según la pestaña.
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onActividad(); if (pestana == 0) mostrarNuevaFactura = true else mostrarNuevoServicio = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(if (pestana == 0) "Emitir factura" else "Nuevo servicio") }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            // Pestañas.
            PrimaryTabRow(selectedTabIndex = pestana) {
                Tab(selected = pestana == 0, onClick = { pestana = 0 }, text = { Text("Facturas") })
                Tab(selected = pestana == 1, onClick = { pestana = 1 }, text = { Text("Servicios (${servicios.size})") })
            }
            if (admin.cargando) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            if (pestana == 0) {
                // ==================== PESTAÑA FACTURAS ====================
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Explicacion(
                        "Aquí la empresa EMITE las facturas de cada mes. El cliente las ve al buscar su cédula en " +
                            "\"Pagar servicios → Por cédula\" y, cuando paga, la factura pasa a PAGADA aquí mismo."
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Resumen("Por cobrar", pendientes.sumOf { it.monto }, "${pendientes.size} pendientes", RojoEgreso, Modifier.weight(1f))
                        Resumen("Recaudado", pagadas.sumOf { it.monto }, "${pagadas.size} pagadas", VerdeIngreso, Modifier.weight(1f))
                    }
                    // Filtros por estado y por servicio (si no caben, se deslizan de lado).
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        listOf(null to "Todas", "PENDIENTES" to "Pendientes", "PAGADAS" to "Pagadas").forEach { (valor, texto) ->
                            FilterChip(selected = admin.estado == valor, onClick = { onActividad(); viewModel.cargarFacturasAdmin(estado = valor) },
                                label = { Text(texto) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        FilterChip(selected = admin.servicio == null, onClick = { onActividad(); viewModel.cargarFacturasAdmin(servicio = null) },
                            label = { Text("Todos") })
                        servicios.forEach { s ->
                            FilterChip(
                                selected = admin.servicio == s.codigo,
                                onClick = { onActividad(); viewModel.cargarFacturasAdmin(servicio = s.codigo) },
                                label = { Text(s.codigo) },
                                leadingIcon = { Icon(iconoPorClave(s.icono), contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                        }
                    }
                    admin.error?.let { Text(it, color = RojoEgreso, fontSize = 13.sp) }
                }
                // Lista de facturas. Tiene espacio extra abajo para que el botón flotante no tape la última.
                LazyColumn(
                    state = estadoLista,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (!admin.cargando && admin.facturas.isEmpty()) {
                        item { Text("No hay facturas con estos filtros", color = Color.Gray, modifier = Modifier.padding(top = 24.dp)) }
                    }
                    items(admin.facturas, key = { it.codigo }) { f -> TarjetaFacturaAdmin(f) }
                }
            } else {
                // ==================== PESTAÑA SERVICIOS ====================
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        Explicacion(
                            "Catálogo de servicios que se pueden facturar y pagar. Si agregas uno nuevo (ej. gas), " +
                                "aparece enseguida en \"Emitir factura\" y en el pago de servicios de los clientes."
                        )
                    }
                    items(servicios, key = { it.codigo }) { s ->
                        // Cuántas facturas tiene este servicio (de la lista cargada).
                        val total = admin.facturas.count { it.servicio == s.codigo }
                        TarjetaServicio(s, total)
                    }
                }
            }
        }
    }

    // Ventana "Emitir factura" (solo aparece si se tocó el botón).
    if (mostrarNuevaFactura) {
        // Nombres ya conocidos por cédula: al escribir una cédula que ya tiene facturas, el nombre se completa solo.
        val titulares = admin.facturas.mapNotNull { f -> f.cedula?.let { it to f.titular } }.toMap()
        EmitirFacturaDialog(
            servicios = servicios,
            titularesConocidos = titulares,
            onCrear = viewModel::crearFactura,
            onActividad = onActividad,
            // Al emitirla se cierra la ventana; el aviso "Nueva factura ..." llega solo desde el servidor.
            onCreada = { mostrarNuevaFactura = false },
            onCancelar = { mostrarNuevaFactura = false }
        )
    }
    // Ventana "Nuevo servicio" (solo aparece si se tocó el botón).
    if (mostrarNuevoServicio) {
        NuevoServicioDialog(
            onCrear = viewModel::crearServicio,
            onActividad = onActividad,
            onCreado = { s ->
                mostrarNuevoServicio = false
                alcance.launch { snackbar.showSnackbar("✔ Servicio ${s.nombre} (${s.codigo}) agregado") }
            },
            onCancelar = { mostrarNuevoServicio = false }
        )
    }
}

// Caja de ayuda con un ícono de información: explica para qué sirve cada pestaña.
@Composable
private fun Explicacion(texto: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AzulTarjetaClaro.copy(alpha = 0.12f))
            .padding(10.dp)
    ) {
        Icon(Icons.Filled.Info, contentDescription = null, tint = AzulTarjetaClaro, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(texto, fontSize = 12.sp)
    }
}

// Recuadro del resumen: título, monto grande y un texto pequeño.
@Composable
private fun Resumen(titulo: String, monto: Double, detalle: String, color: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(titulo, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatearMonto(monto), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = color)
            Text(detalle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// Círculo azul con el ícono de un servicio.
@Composable
private fun CirculoServicio(icono: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(AzulTarjeta, AzulTarjetaClaro)))
    ) { Icon(icono, contentDescription = null, tint = Color.White) }
}

// Tarjeta de UNA factura: servicio, titular, cédula, mes, contrato, monto y estado.
@Composable
private fun TarjetaFacturaAdmin(f: Factura) {
    val colorEstado = if (f.pagado) VerdeIngreso else RojoEgreso
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
            CirculoServicio(iconoDeServicio(f.servicio))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(f.titular, fontWeight = FontWeight.SemiBold)
                Text("C.I. ${f.cedula ?: "—"} · ${f.periodo ?: "Sin periodo"}", fontSize = 12.sp, color = Color.Gray)
                Text("${f.servicio} · Contrato ${f.codigo}", fontSize = 12.sp, color = Color.Gray)
                if (f.pagado) {
                    Text(
                        "Pagada por la cuenta ${f.pagadoPor ?: "—"}" + (f.fechaPago?.let { " · ${formatearFecha(it)}" } ?: ""),
                        fontSize = 11.sp, color = VerdeIngreso
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatearMonto(f.monto), fontWeight = FontWeight.Bold)
                // Etiqueta de estado con fondo suave del mismo color.
                Text(
                    if (f.pagado) "PAGADA" else "PENDIENTE",
                    color = colorEstado, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(colorEstado.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

// Tarjeta de UN servicio del catálogo: ícono, nombre, código y cuántas facturas tiene.
@Composable
private fun TarjetaServicio(s: Servicio, facturas: Int) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
            CirculoServicio(iconoPorClave(s.icono))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(s.nombre, fontWeight = FontWeight.SemiBold)
                Text("Código ${s.codigo}", fontSize = 12.sp, color = Color.Gray)
            }
            Text("$facturas facturas", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// Mes actual en español con mayúscula inicial (ej. "Octubre 2026"): valor inicial del periodo.
private fun periodoActual(): String {
    val hoy = LocalDate.now()
    val mes = hoy.month.getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es"))
    return mes.replaceFirstChar { it.uppercase() } + " " + hoy.year
}

// VENTANA "EMITIR FACTURA": la empresa le cobra a una persona (por su cédula) el servicio de un mes.
// Aquí se revisa que la cédula sea real y que el monto sea correcto; el servidor crea el código de contrato.
@Composable
private fun EmitirFacturaDialog(
    servicios: List<Servicio>,
    titularesConocidos: Map<String, String>,    // Cédula -> nombre (de facturas anteriores).
    onCrear: suspend (cedula: String, titular: String, servicio: String, periodo: String, monto: Double) -> Resultado<Factura>,
    onActividad: () -> Unit,
    onCreada: (Factura) -> Unit,
    onCancelar: () -> Unit
) {
    var cedula by remember { mutableStateOf("") }
    var titular by remember { mutableStateOf("") }
    var servicio by remember { mutableStateOf(servicios.firstOrNull()?.codigo ?: "LUZ") }
    var periodo by remember { mutableStateOf(periodoActual()) }
    var monto by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var guardando by remember { mutableStateOf(false) }
    val alcance = rememberCoroutineScope()

    // Escribir en la ventana cuenta como actividad (para que no se cierre la sesión).
    LaunchedEffect(cedula, titular, servicio, periodo, monto) { onActividad() }

    // Revisa los datos y, si todo está bien, pide al servidor crear la factura.
    fun crear() {
        val valor = monto.replace(',', '.').toDoubleOrNull()
        // Revisión en el teléfono (el servidor vuelve a revisar lo mismo).
        error = when {
            !cedulaValida(cedula) -> "Cédula no válida (10 dígitos con dígito verificador correcto)"
            titular.trim().length < 3 -> "Escribe el nombre del titular"
            periodo.trim().length < 3 -> "Escribe el periodo (ej. Diciembre 2026)"
            valor == null || valor <= 0 -> "El monto debe ser un número mayor que 0"
            else -> null
        }
        if (error != null || valor == null) return
        alcance.launch {
            guardando = true
            when (val r = onCrear(cedula, titular.trim(), servicio, periodo.trim(), valor)) {
                is Resultado.Exito -> onCreada(r.datos)
                is Resultado.Error -> error = r.mensaje
            }
            guardando = false
        }
    }

    AlertDialog(
        onDismissRequest = onCancelar,
        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
        title = { Text("Emitir factura") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Cobra a una persona el servicio de un mes. Ella la verá al buscar su cédula en \"Pagar servicios\".",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = cedula,
                    onValueChange = { nuevo ->
                        cedula = nuevo.filter { c -> c.isDigit() }.take(10); error = null
                        // Si ya conocemos a esa persona y el nombre está vacío, lo completamos.
                        if (titular.isBlank()) titularesConocidos[cedula]?.let { titular = it }
                    },
                    label = { Text("Cédula del titular") },
                    placeholder = { Text("Ej. 1712345675") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = titular,
                    onValueChange = { titular = it; error = null },
                    label = { Text("Nombre del titular") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Servicio", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    servicios.forEach { s ->
                        FilterChip(
                            selected = servicio == s.codigo, onClick = { servicio = s.codigo },
                            label = { Text(s.codigo, fontSize = 12.sp) },
                            leadingIcon = { Icon(iconoPorClave(s.icono), contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                    }
                }
                OutlinedTextField(
                    value = periodo,
                    onValueChange = { periodo = it; error = null },
                    label = { Text("Mes que se cobra") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = monto,
                    onValueChange = { monto = it.trim(); error = null },
                    label = { Text("Valor a pagar") },
                    prefix = { Text("$ ") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it, color = RojoEgreso, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            Button(onClick = { crear() }, enabled = !guardando) {
                if (guardando) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                else Text("Emitir")
            }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } }
    )
}

// Código sugerido a partir del nombre: solo letras, sin tildes, en mayúsculas y hasta 10 (ej. "Gas doméstico" -> "GAS").
private fun codigoSugerido(nombre: String): String {
    val sinTildes = java.text.Normalizer.normalize(nombre, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
    return sinTildes.trim().substringBefore(" ").uppercase().filter { it in 'A'..'Z' }.take(10)
}

// VENTANA "NUEVO SERVICIO": nombre, código y el ícono con que se mostrará en la app.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NuevoServicioDialog(
    onCrear: suspend (codigo: String, nombre: String, icono: String) -> Resultado<Servicio>,
    onActividad: () -> Unit,
    onCreado: (Servicio) -> Unit,
    onCancelar: () -> Unit
) {
    var nombre by remember { mutableStateOf("") }
    var codigo by remember { mutableStateOf("") }
    var codigoEditado by remember { mutableStateOf(false) }     // Si el usuario lo cambió a mano, no lo pisamos.
    var icono by remember { mutableStateOf("OTRO") }
    var error by remember { mutableStateOf<String?>(null) }
    var guardando by remember { mutableStateOf(false) }
    val alcance = rememberCoroutineScope()

    // Escribir en la ventana cuenta como actividad.
    LaunchedEffect(nombre, codigo, icono) { onActividad() }

    // Revisa el nombre y el código y, si están bien, pide al servidor crear el servicio.
    fun crear() {
        error = when {
            nombre.trim().length < 3 -> "Escribe el nombre del servicio"
            !Regex("^[A-Z]{3,10}$").matches(codigo) -> "El código debe tener de 3 a 10 letras (ej. GAS)"
            else -> null
        }
        if (error != null) return
        alcance.launch {
            guardando = true
            when (val r = onCrear(codigo, nombre.trim(), icono)) {
                is Resultado.Exito -> onCreado(r.datos)
                is Resultado.Error -> error = r.mensaje                 // Ej. "Ya existe un servicio con el código GAS".
            }
            guardando = false
        }
    }

    AlertDialog(
        onDismissRequest = onCancelar,
        icon = { Icon(iconoPorClave(icono), contentDescription = null) },
        title = { Text("Nuevo servicio") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it; error = null; if (!codigoEditado) codigo = codigoSugerido(it) },
                    label = { Text("Nombre (ej. Gas doméstico)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = codigo,
                    onValueChange = { codigo = it.uppercase().filter { c -> c in 'A'..'Z' }.take(10); codigoEditado = true; error = null },
                    label = { Text("Código (3 a 10 letras)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Ícono", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // Rejilla de íconos: el elegido queda marcado con un borde azul.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CLAVES_ICONO.forEach { clave ->
                        val elegido = icono == clave
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (elegido) AzulTarjetaClaro.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant)
                                .border(2.dp, if (elegido) AzulTarjetaClaro else Color.Transparent, CircleShape)
                                .clickable { icono = clave }
                        ) { Icon(iconoPorClave(clave), contentDescription = clave) }
                    }
                }
                error?.let { Text(it, color = RojoEgreso, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            Button(onClick = { crear() }, enabled = !guardando) {
                if (guardando) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                else Text("Agregar")
            }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } }
    )
}
