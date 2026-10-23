// Paquete de la interfaz gráfica.
package com.example.carriel_cuenca_billeteradistribuida.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout        // Ícono de cerrar sesión.
import androidx.compose.material.icons.automirrored.filled.ReceiptLong   // Versión que se voltea en idiomas RTL.
import androidx.compose.material.icons.filled.ChevronLeft                // Flecha izquierda del filtro.
import androidx.compose.material.icons.filled.ChevronRight               // Flecha derecha del filtro.
import androidx.compose.material.icons.filled.Science                    // Ícono del panel de pruebas.
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carriel_cuenca_billeteradistribuida.model.EstadoConexion
import com.example.carriel_cuenca_billeteradistribuida.model.Movimiento
import com.example.carriel_cuenca_billeteradistribuida.model.TipoOperacion
import com.example.carriel_cuenca_billeteradistribuida.ui.components.FilaMovimiento
import com.example.carriel_cuenca_billeteradistribuida.ui.components.PanelAcciones
import com.example.carriel_cuenca_billeteradistribuida.ui.components.TarjetaSaldo
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.Carriel_Cuenca_BilleteraDistribuidaTheme
import java.time.LocalDate
import kotlin.math.ceil

// Cuántos movimientos se muestran por página.
private const val POR_PAGINA = 10

// =====================================================================
// PANTALLA PRINCIPAL (Dashboard).
// La parte de ARRIBA (saludo, saldo, botones y filtro) es FIJA; solo la lista de
// movimientos se desplaza. El historial se filtra POR DÍA y se pagina de 10 en 10.
// =====================================================================
@Composable
fun DashboardScreen(
    nombre: String,
    usuario: String,
    cuenta: String,
    saldo: Double?,
    estadoConexion: EstadoConexion,
    movimientos: List<Movimiento>,
    procesando: Boolean,
    onAccionClick: (TipoOperacion) -> Unit,
    onCerrarSesion: () -> Unit,
    onAbrirPruebas: () -> Unit,
    esLaboratorio: Boolean,
    modifier: Modifier = Modifier
) {
    // Agrupamos los movimientos por DÍA. "remember(movimientos)": solo se recalcula si cambian.
    val porDia = remember(movimientos) { movimientos.groupBy { diaDe(it.fechaHora) } }
    // Lista de días que tienen movimientos, del más reciente al más antiguo.
    val dias = remember(porDia) { porDia.keys.sortedDescending() }

    // Día seleccionado en el filtro. null = usar el más reciente (por defecto, el de hoy).
    var diaSel by remember { mutableStateOf<LocalDate?>(null) }
    val diaActual = diaSel?.takeIf { it in dias } ?: dias.firstOrNull()
    val indiceDia = dias.indexOf(diaActual)

    // Página dentro del día. Se reinicia a 0 cada vez que cambia el día (key = diaActual).
    var pagina by remember(diaActual) { mutableStateOf(0) }

    val movimientosDelDia = diaActual?.let { porDia[it].orEmpty() } ?: emptyList()
    val totalPaginas = maxOf(1, ceil(movimientosDelDia.size / POR_PAGINA.toDouble()).toInt())
    val paginaOk = pagina.coerceIn(0, totalPaginas - 1)
    val visibles = movimientosDelDia.drop(paginaOk * POR_PAGINA).take(POR_PAGINA)

    // Si llega un movimiento NUEVO (ej. una transferencia por WebSocket), volvemos al día más
    // reciente y a la página 1 para que se vea al instante, aunque se estuviera mirando otro día.
    val masReciente = movimientos.firstOrNull()?.id
    LaunchedEffect(masReciente) {
        diaSel = null
        pagina = 0
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ---------- PARTE FIJA ----------
        // Saludo + botón de pruebas (solo laboratorio) + cerrar sesión.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Hola, ${nombre.substringBefore(" ")}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("@$usuario", color = Color.Gray)
            }
            if (esLaboratorio) {
                IconButton(onClick = onAbrirPruebas) { Icon(Icons.Filled.Science, contentDescription = "Pruebas de concurrencia") }
            }
            IconButton(onClick = onCerrarSesion) { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Cerrar sesión") }
        }
        TarjetaSaldo(saldo = saldo, cuenta = cuenta, estadoConexion = estadoConexion)
        PanelAcciones(habilitado = !procesando, onAccionClick = onAccionClick)

        // Título "Historial" con el FILTRO DE FECHA (flechas para cambiar de día).
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Historial", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (diaActual != null) {
                // ‹ va a un día MÁS ANTIGUO (índice mayor); › a uno MÁS RECIENTE (índice menor).
                IconButton(onClick = { dias.getOrNull(indiceDia + 1)?.let { diaSel = it } }, enabled = indiceDia < dias.size - 1) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "Día anterior")
                }
                Text(etiquetaDia(diaActual), fontWeight = FontWeight.SemiBold)
                IconButton(onClick = { dias.getOrNull(indiceDia - 1)?.let { diaSel = it } }, enabled = indiceDia > 0) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "Día siguiente")
                }
            }
        }

        // ---------- PARTE QUE SE DESPLAZA ----------
        if (movimientos.isEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(56.dp))
                Text("Aún no tienes movimientos", color = Color.Gray)
                Text("Tus comprobantes aparecerán aquí", color = Color.LightGray)
            }
        } else {
            // "weight(1f)": la lista ocupa el espacio que sobra; la cabecera de arriba queda fija.
            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(visibles, key = { it.id }) { movimiento -> FilaMovimiento(movimiento = movimiento) }
                }
            }

            // Paginación: solo aparece si el día tiene más de 10 movimientos.
            if (totalPaginas > 1) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(onClick = { pagina = paginaOk - 1 }, enabled = paginaOk > 0) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "Página anterior")
                    }
                    Text("Página ${paginaOk + 1} de $totalPaginas", fontSize = 13.sp)
                    IconButton(onClick = { pagina = paginaOk + 1 }, enabled = paginaOk < totalPaginas - 1) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = "Página siguiente")
                    }
                }
            }
        }
    }
}

// VISTA PREVIA en Android Studio (pestaña "Split" o "Design").
@Preview(showBackground = true, showSystemUi = true)
@Composable
fun DashboardPreview() {
    Carriel_Cuenca_BilleteraDistribuidaTheme {
        DashboardScreen(
            nombre = "Pamela Carriel",
            usuario = "pamela",
            cuenta = "1001",
            saldo = DatosDePrueba.saldo,
            estadoConexion = EstadoConexion.EN_LINEA,
            movimientos = DatosDePrueba.movimientos,
            procesando = false,
            onAccionClick = { },
            onCerrarSesion = { },
            onAbrirPruebas = { },
            esLaboratorio = true
        )
    }
}
