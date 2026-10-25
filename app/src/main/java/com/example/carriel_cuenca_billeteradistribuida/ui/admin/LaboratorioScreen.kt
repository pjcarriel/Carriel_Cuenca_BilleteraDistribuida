// =====================================================================
//   La pantalla de inicio del ADMINISTRADOR (usuario laboratorio): muestra
//   la conexión, los teléfonos conectados, el resumen de cobros y sus accesos.
// =====================================================================

package com.example.carriel_cuenca_billeteradistribuida.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carriel_cuenca_billeteradistribuida.model.EstadoAdmin
import com.example.carriel_cuenca_billeteradistribuida.model.EstadoConexion
import com.example.carriel_cuenca_billeteradistribuida.model.NodosConectados
import com.example.carriel_cuenca_billeteradistribuida.model.Servicio
import com.example.carriel_cuenca_billeteradistribuida.ui.components.EstadoConexionChip
import com.example.carriel_cuenca_billeteradistribuida.ui.components.TarjetaNodos
import com.example.carriel_cuenca_billeteradistribuida.ui.formatearMonto
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.AzulTarjeta
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.AzulTarjetaClaro
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.RojoEgreso
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.VerdeIngreso

// Inicio del administrador: no ve saldo ni historial, sino el estado del sistema.
@Composable
fun LaboratorioScreen(
    estadoConexion: EstadoConexion,     // Si está conectado al servidor.
    nodosConectados: NodosConectados?,  // Teléfonos conectados ahora mismo.
    admin: EstadoAdmin,                 // Facturas (para el resumen de cobros).
    servicios: List<Servicio>,          // Lista de servicios (para mostrar cuántos hay).
    onAbrirServicios: () -> Unit,       // Abre "Servicios y facturas".
    onAbrirPruebas: () -> Unit,         // Abre "Pruebas de concurrencia".
    onCerrarSesion: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Datos para la pantalla: si hay conexión y las facturas separadas en pendientes y pagadas.
    val enLinea = estadoConexion == EstadoConexion.EN_LINEA
    val pendientes = admin.facturas.filter { !it.pagado }
    val pagadas = admin.facturas.filter { it.pagado }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ---------- ENCABEZADO ----------
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Panel de administración", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("@laboratorio", color = Color.Gray)
            }
            IconButton(onClick = onCerrarSesion) { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Cerrar sesión") }
        }

        // ---------- TARJETA DEL ADMINISTRADOR (con el estado de la conexión) ----------
        Card(shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.linearGradient(listOf(AzulTarjeta, AzulTarjetaClaro)))
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.AdminPanelSettings, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Administrador del sistema", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                        modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Empresa de servicios básicos y laboratorio de pruebas de Billetera CC.",
                    color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp
                )
                Spacer(Modifier.height(12.dp))
                EstadoConexionChip(estadoConexion)
            }
        }

        // ---------- NODOS CONECTADOS (tiempo real) ----------
        TarjetaNodos(nodos = nodosConectados, enLinea = enLinea)

        // ---------- RESUMEN DE COBROS: cuánto falta cobrar y cuánto ya se cobró ----------
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ResumenInicio("Por cobrar", pendientes.sumOf { it.monto }, "${pendientes.size} facturas pendientes", RojoEgreso, Modifier.weight(1f))
            ResumenInicio("Recaudado", pagadas.sumOf { it.monto }, "${pagadas.size} facturas pagadas", VerdeIngreso, Modifier.weight(1f))
        }

        // ---------- ACCESOS A LAS OTRAS SECCIONES ----------
        OpcionMenu(
            icono = Icons.Filled.Storefront,
            titulo = "Servicios y facturas",
            descripcion = "${servicios.size} servicios. Agrega servicios, emite facturas y mira los pagos en tiempo real.",
            onClick = onAbrirServicios
        )
        OpcionMenu(
            icono = Icons.Filled.Science,
            titulo = "Pruebas de concurrencia",
            descripcion = "Lanza muchas operaciones a la vez y mira qué hilos las atienden.",
            onClick = onAbrirPruebas
        )
    }
}

// Recuadro del resumen de cobros: título, total en dinero y un texto pequeño.
@Composable
private fun ResumenInicio(titulo: String, monto: Double, detalle: String, color: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(titulo, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatearMonto(monto), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = color)
            Text(detalle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// Botón grande del menú: ícono en círculo, título, descripción y flecha. Al tocarlo abre esa sección.
@Composable
private fun OpcionMenu(icono: ImageVector, titulo: String, descripcion: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(AzulTarjeta, AzulTarjetaClaro)))
            ) { Icon(icono, contentDescription = null, tint = Color.White) }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(titulo, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(descripcion, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.Gray)
        }
    }
}
