// Paquete de la interfaz gráfica.
package com.example.carriel_cuenca_billeteradistribuida.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout        // Ícono de cerrar sesión.
import androidx.compose.material.icons.filled.Science                    // Ícono del panel de pruebas.
import androidx.compose.material.icons.automirrored.filled.ReceiptLong   // Versión que se voltea en idiomas de derecha a izquierda.
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.carriel_cuenca_billeteradistribuida.model.EstadoConexion
import com.example.carriel_cuenca_billeteradistribuida.model.Movimiento
import com.example.carriel_cuenca_billeteradistribuida.model.TipoOperacion
import com.example.carriel_cuenca_billeteradistribuida.ui.components.FilaMovimiento
import com.example.carriel_cuenca_billeteradistribuida.ui.components.PanelAcciones
import com.example.carriel_cuenca_billeteradistribuida.ui.components.TarjetaSaldo
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.Carriel_Cuenca_BilleteraDistribuidaTheme

// =====================================================================
// PANTALLA PRINCIPAL (Dashboard): solo ENSAMBLA los componentes de ui/components.
// Recibe los datos por parámetro; no sabe de dónde vienen (Room, servidor o datos de prueba).
// =====================================================================
@Composable
fun DashboardScreen(
    nombre: String,                              // Nombre del usuario de la sesión.
    usuario: String,                             // Usuario (ej. "pamela").
    cuenta: String,                              // Número de cuenta de la sesión.
    saldo: Double?,                              // Saldo de la tarjeta (null = aún desconocido).
    estadoConexion: EstadoConexion,              // Estado del WebSocket (verde/ámbar/rojo).
    movimientos: List<Movimiento>,               // Historial a mostrar.
    procesando: Boolean,                         // true = hay una operación en curso (botones desactivados).
    onAccionClick: (TipoOperacion) -> Unit,      // Lambda: avisa qué botón se tocó.
    onCerrarSesion: () -> Unit,                  // Botón de cerrar sesión.
    onAbrirPruebas: () -> Unit,                  // Botón del panel de pruebas de concurrencia.
    esLaboratorio: Boolean,                      // Solo el usuario "laboratorio" ve el panel.
    modifier: Modifier = Modifier
) {
    // Una sola LazyColumn para toda la pantalla: todo se desplaza junto.
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Saludo con el nombre y botón de cerrar sesión.
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    // substringBefore(" "): solo el primer nombre ("Pamela Carriel" -> "Pamela").
                    Text("Hola, ${nombre.substringBefore(" ")}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("@$usuario", color = Color.Gray)
                }
                // El panel de pruebas solo lo ve el usuario "laboratorio".
                if (esLaboratorio) {
                    IconButton(onClick = onAbrirPruebas) {
                        Icon(Icons.Filled.Science, contentDescription = "Pruebas de concurrencia")
                    }
                }
                IconButton(onClick = onCerrarSesion) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Cerrar sesión")
                }
            }
        }
        item { TarjetaSaldo(saldo = saldo, cuenta = cuenta, estadoConexion = estadoConexion) }
        item { PanelAcciones(habilitado = !procesando, onAccionClick = onAccionClick) }
        item {
            Text(
                text = "Historial",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // Estado vacío: ícono y texto centrados en lugar de una lista vacía.
        if (movimientos.isEmpty()) {
            item {
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
            }
        }

        // Una fila por movimiento; "key" identifica cada fila por su id (animaciones y rendimiento).
        items(movimientos, key = { it.id }) { movimiento ->
            FilaMovimiento(movimiento = movimiento)
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
