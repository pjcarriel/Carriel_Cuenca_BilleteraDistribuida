// =====================================================================
//   Es la PANTALLA PRINCIPAL del cliente: saludo, saldo, botones de
//   operaciones y el historial de movimientos filtrado por día.
// =====================================================================

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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carriel_cuenca_billeteradistribuida.model.EstadoConexion
import com.example.carriel_cuenca_billeteradistribuida.model.Movimiento
import com.example.carriel_cuenca_billeteradistribuida.ui.components.AvisoSinConexion
import com.example.carriel_cuenca_billeteradistribuida.model.TipoOperacion
import com.example.carriel_cuenca_billeteradistribuida.ui.components.FilaMovimiento
import com.example.carriel_cuenca_billeteradistribuida.ui.components.PanelAcciones
import com.example.carriel_cuenca_billeteradistribuida.ui.components.TarjetaSaldo
import java.time.LocalDate
import kotlin.math.ceil

// Cuántos movimientos se muestran por página.
private const val POR_PAGINA = 10

// Pantalla principal: arriba lo fijo (saludo, saldo, botones), abajo el historial.
@Composable
fun DashboardScreen(
    nombre: String,                               // Nombre completo del usuario.
    usuario: String,
    cuenta: String,
    saldo: Double?,                               // null = todavía no se conoce.
    estadoConexion: EstadoConexion,               // En línea, conectando o desconectado.
    movimientos: List<Movimiento>,                // Historial, del más nuevo al más antiguo.
    procesando: Boolean,                          // true = hay una operación en curso.
    onAccionClick: (TipoOperacion) -> Unit,       // Se tocó un botón de operación.
    onCerrarSesion: () -> Unit,
    onVerDetalle: (Movimiento) -> Unit = {},                       // Se tocó un movimiento.
    modifier: Modifier = Modifier
) {
    // Solo se puede operar con conexión; sin ella, el saldo y el historial se siguen viendo.
    val enLinea = estadoConexion == EstadoConexion.EN_LINEA

    // Movimientos agrupados por día, y la lista de días (del más reciente al más antiguo).
    val porDia = remember(movimientos) { movimientos.groupBy { diaDe(it.fechaHora) } }
    val dias = remember(porDia) { porDia.keys.sortedDescending() }

    // Día elegido en el filtro (null = el más reciente).
    var diaSel by remember { mutableStateOf<LocalDate?>(null) }
    val diaActual = diaSel?.takeIf { it in dias } ?: dias.firstOrNull()
    val indiceDia = dias.indexOf(diaActual)

    // Página actual; vuelve a la primera al cambiar de día.
    var pagina by remember(diaActual) { mutableStateOf(0) }

    // Movimientos del día, total de páginas y los 10 que se ven ahora.
    val movimientosDelDia = diaActual?.let { porDia[it].orEmpty() } ?: emptyList()
    val totalPaginas = maxOf(1, ceil(movimientosDelDia.size / POR_PAGINA.toDouble()).toInt())
    val paginaOk = pagina.coerceIn(0, totalPaginas - 1)
    val visibles = movimientosDelDia.drop(paginaOk * POR_PAGINA).take(POR_PAGINA)

    // Si llega un movimiento nuevo, volver a "Hoy" y a la página 1 para que se vea.
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
        // Saludo con el primer nombre y botón de cerrar sesión.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Hola, ${nombre.substringBefore(" ")}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("@$usuario", color = Color.Gray)
            }
            IconButton(onClick = onCerrarSesion) { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Cerrar sesión") }
        }
        // Tarjeta azul con el saldo.
        TarjetaSaldo(saldo = saldo, cuenta = cuenta, estadoConexion = estadoConexion)
        // Botones de operación (apagados si no hay conexión o hay una operación en curso).
        PanelAcciones(habilitado = !procesando && enLinea, onAccionClick = onAccionClick)
        // Aviso de "sin conexión".
        if (!enLinea) AvisoSinConexion(conectando = estadoConexion == EstadoConexion.CONECTANDO)

        // Título "Historial" con las flechas para cambiar de día.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Historial", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (diaActual != null) {
                // ‹ = día anterior, › = día siguiente.
                IconButton(onClick = { dias.getOrNull(indiceDia + 1)?.let { diaSel = it } }, enabled = indiceDia < dias.size - 1) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "Día anterior")
                }
                Text(etiquetaDia(diaActual), fontWeight = FontWeight.SemiBold)    // "Hoy", "Ayer" o la fecha.
                IconButton(onClick = { dias.getOrNull(indiceDia - 1)?.let { diaSel = it } }, enabled = indiceDia > 0) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "Día siguiente")
                }
            }
        }

        // Sin movimientos: ícono y mensaje "Aún no tienes movimientos".
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
            // Lista de movimientos que se puede desplazar.
            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(visibles, key = { it.id }) { movimiento ->
                        // Al tocarlo se abre su detalle.
                        FilaMovimiento(movimiento = movimiento, onClick = { onVerDetalle(movimiento) })
                    }
                }
            }

            // Botones de página (solo si hay más de 10 movimientos ese día).
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
