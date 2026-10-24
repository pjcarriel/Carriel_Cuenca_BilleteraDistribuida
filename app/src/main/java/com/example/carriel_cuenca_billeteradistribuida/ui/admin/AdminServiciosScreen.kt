// Paquete del panel de ADMINISTRACIÓN DE SERVICIOS (solo usuario laboratorio).
package com.example.carriel_cuenca_billeteradistribuida.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.example.carriel_cuenca_billeteradistribuida.ui.BilleteraViewModel
import com.example.carriel_cuenca_billeteradistribuida.ui.components.iconoDeServicio
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

// Servicios básicos (los mismos que en el servidor).
private val SERVICIOS = listOf("LUZ", "AGUA", "INTERNET")

// PANEL DE ADMINISTRACIÓN DE SERVICIOS: el laboratorio actúa como la "empresa" de luz, agua e internet.
//  - Resumen: cuánto falta por cobrar y cuánto se ha recaudado.
//  - Filtros por estado (pendientes / pagadas) y por servicio.
//  - Lista de facturas que se ACTUALIZA SOLA (WebSocket) cuando alguien paga desde su celular.
//  - Botón para crear facturas nuevas (cédula, titular, servicio, periodo y monto).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminServiciosScreen(
    viewModel: BilleteraViewModel,      // El mismo ViewModel del dashboard (misma sesión y WebSocket).
    onVolver: () -> Unit,
    onActividad: () -> Unit             // Usar el panel cuenta como actividad (no se cierra la sesión).
) {
    val admin by viewModel.admin.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var mostrarNueva by remember { mutableStateOf(false) }     // Diálogo "Nueva factura".
    // Estado de desplazamiento de la lista: al recargar volvemos ARRIBA (ahí están las pendientes).
    // (Sin esto, LazyColumn conserva la posición de la factura que estaba visible.)
    val estadoLista = rememberLazyListState()
    LaunchedEffect(admin.facturas) { estadoLista.scrollToItem(0) }

    // Al abrir: cargar la lista. Al salir (onDispose): avisar al ViewModel para que no recargue más.
    LaunchedEffect(Unit) { viewModel.cargarFacturasAdmin() }
    DisposableEffect(Unit) { onDispose { viewModel.cerrarAdmin() } }
    // Avisos en tiempo real (alguien pagó): se muestran abajo. La lista ya se recarga en el ViewModel.
    LaunchedEffect(Unit) {
        viewModel.avisosFacturas.collect { detalle -> onActividad(); snackbar.showSnackbar("🔔 $detalle") }
    }

    // Resumen calculado a partir de la lista que se está viendo.
    val pendientes = admin.facturas.filter { !it.pagado }
    val pagadas = admin.facturas.filter { it.pagado }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Administrar servicios") },
                navigationIcon = { IconButton(onClick = onVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") } },
                actions = { IconButton(onClick = { onActividad(); viewModel.cargarFacturasAdmin() }) { Icon(Icons.Filled.Refresh, contentDescription = "Recargar") } }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        // Botón flotante abajo a la derecha para crear una factura.
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onActividad(); mostrarNueva = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Nueva factura") }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (admin.cargando) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // ---------- RESUMEN ----------
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Resumen("Por cobrar", pendientes.sumOf { it.monto }, "${pendientes.size} pendientes", RojoEgreso, Modifier.weight(1f))
                    Resumen("Recaudado", pagadas.sumOf { it.monto }, "${pagadas.size} pagadas", VerdeIngreso, Modifier.weight(1f))
                }
                // ---------- FILTROS (horizontalScroll: si no caben, se deslizan a los lados) ----------
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    listOf(null to "Todas", "PENDIENTES" to "Pendientes", "PAGADAS" to "Pagadas").forEach { (valor, texto) ->
                        FilterChip(selected = admin.estado == valor, onClick = { onActividad(); viewModel.cargarFacturasAdmin(estado = valor) },
                            label = { Text(texto) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    FilterChip(selected = admin.servicio == null, onClick = { onActividad(); viewModel.cargarFacturasAdmin(servicio = null) },
                        label = { Text("Todos") })
                    SERVICIOS.forEach { s ->
                        FilterChip(
                            selected = admin.servicio == s,
                            onClick = { onActividad(); viewModel.cargarFacturasAdmin(servicio = s) },
                            label = { Text(s) },
                            leadingIcon = { Icon(iconoDeServicio(s), contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                    }
                }
                admin.error?.let { Text(it, color = RojoEgreso, fontSize = 13.sp) }
            }

            // ---------- LISTA DE FACTURAS ----------
            // contentPadding inferior: el último elemento no queda tapado por el botón flotante.
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
        }
    }

    if (mostrarNueva) {
        NuevaFacturaDialog(
            onCrear = viewModel::crearFactura,
            onActividad = onActividad,
            // Al crearla se cierra el diálogo; el aviso "Nueva factura ..." llega solo por WebSocket.
            onCreada = { mostrarNueva = false },
            onCancelar = { mostrarNueva = false }
        )
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

// Tarjeta de UNA factura en el panel: servicio, titular, cédula, mes, contrato, monto y estado.
@Composable
private fun TarjetaFacturaAdmin(f: Factura) {
    val colorEstado = if (f.pagado) VerdeIngreso else RojoEgreso
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
            // Ícono del servicio en un círculo azul.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(AzulTarjeta, AzulTarjetaClaro)))
            ) { Icon(iconoDeServicio(f.servicio), contentDescription = f.servicio, tint = Color.White) }
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

// Mes actual en español con mayúscula inicial (ej. "Octubre 2026"): valor inicial del periodo.
private fun periodoActual(): String {
    val hoy = LocalDate.now()
    val mes = hoy.month.getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es"))
    return mes.replaceFirstChar { it.uppercase() } + " " + hoy.year
}

// DIÁLOGO "NUEVA FACTURA": valida los datos aquí (cédula con el algoritmo, monto) y la crea en el servidor.
@Composable
private fun NuevaFacturaDialog(
    onCrear: suspend (cedula: String, titular: String, servicio: String, periodo: String, monto: Double) -> Resultado<Factura>,
    onActividad: () -> Unit,
    onCreada: (Factura) -> Unit,
    onCancelar: () -> Unit
) {
    var cedula by remember { mutableStateOf("") }
    var titular by remember { mutableStateOf("") }
    var servicio by remember { mutableStateOf(SERVICIOS.first()) }
    var periodo by remember { mutableStateOf(periodoActual()) }
    var monto by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var guardando by remember { mutableStateOf(false) }
    val alcance = rememberCoroutineScope()

    // Escribir en el diálogo cuenta como actividad (los diálogos no los ve el detector de toques).
    LaunchedEffect(cedula, titular, servicio, periodo, monto) { onActividad() }

    fun crear() {
        val valor = monto.replace(',', '.').toDoubleOrNull()
        // Validaciones locales (el servidor valida lo mismo otra vez).
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
        title = { Text("Nueva factura") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = cedula,
                    onValueChange = { cedula = it.filter { c -> c.isDigit() }.take(10); error = null },
                    label = { Text("Cédula del titular") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = titular,
                    onValueChange = { titular = it; error = null },
                    label = { Text("Titular") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SERVICIOS.forEach { s ->
                        FilterChip(selected = servicio == s, onClick = { servicio = s }, label = { Text(s, fontSize = 12.sp) })
                    }
                }
                OutlinedTextField(
                    value = periodo,
                    onValueChange = { periodo = it; error = null },
                    label = { Text("Periodo (mes)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = monto,
                    onValueChange = { monto = it.trim(); error = null },
                    label = { Text("Monto") },
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
                else Text("Crear factura")
            }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } }
    )
}
