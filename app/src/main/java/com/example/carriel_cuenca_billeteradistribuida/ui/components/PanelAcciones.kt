// =====================================================================
//   Los 4 botones grandes de la pantalla principal: Depositar, Retirar,
//   Transferir y Pagar Servicios. Al tocar uno se abre su ventana.
// =====================================================================

package com.example.carriel_cuenca_billeteradistribuida.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalAtm
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.carriel_cuenca_billeteradistribuida.model.TipoOperacion

// Panel con los 4 botones. Solo avisa qué botón se tocó; la pantalla abre la ventana.
// Si "habilitado" es false, los botones se bloquean.
@Composable
fun PanelAcciones(habilitado: Boolean, onAccionClick: (TipoOperacion) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {  // Dos filas separadas 12 dp.
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {  // Primera fila: depositar y retirar.
            // Cada botón ocupa la mitad del ancho. Lo que va entre { } es lo que pasa al tocarlo.
            BotonAccion("Depositar", Icons.Filled.Savings, habilitado, Modifier.weight(1f)) {
                onAccionClick(TipoOperacion.DEPOSITO)
            }
            BotonAccion("Retirar", Icons.Filled.LocalAtm, habilitado, Modifier.weight(1f)) {
                onAccionClick(TipoOperacion.RETIRO)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {  // Segunda fila: transferir y pagar servicios.
            BotonAccion("Transferir", Icons.Filled.SwapHoriz, habilitado, Modifier.weight(1f)) {
                onAccionClick(TipoOperacion.TRANSFERENCIA_ENVIADA)
            }
            BotonAccion("Pagar Servicios", Icons.Filled.Receipt, habilitado, Modifier.weight(1f)) {
                onAccionClick(TipoOperacion.PAGO_SERVICIO)
            }
        }
    }
}

// Un botón grande (en forma de tarjeta) con ícono y texto.
@Composable
fun BotonAccion(
    texto: String,                                              // Texto del botón.
    icono: ImageVector,                                         // Ícono que se dibuja.
    habilitado: Boolean,                                        // false = botón bloqueado.
    modifier: Modifier = Modifier,                              // Ajustes de tamaño que manda quien lo usa.
    onClick: () -> Unit                                         // Qué hacer al tocarlo.
) {
    Card(
        onClick = onClick,                                      // La tarjeta se puede tocar como un botón.
        enabled = habilitado,                                   // Bloqueado = se ve gris y no responde.
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        // Ícono y texto uno al lado del otro, centrados.
        Row(
            modifier = Modifier
                .fillMaxSize()                                  // Ocupa toda la tarjeta.
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,         // Centra horizontalmente.
            verticalAlignment = Alignment.CenterVertically      // Centra verticalmente.
        ) {
            Icon(icono, contentDescription = texto, modifier = Modifier.size(22.dp))  // La descripción la lee el lector de pantalla.
            Spacer(Modifier.width(8.dp))
            Text(texto, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
}
