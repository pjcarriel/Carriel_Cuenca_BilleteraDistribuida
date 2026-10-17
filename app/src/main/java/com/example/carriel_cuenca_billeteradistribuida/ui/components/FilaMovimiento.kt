// Paquete de componentes reutilizables.
package com.example.carriel_cuenca_billeteradistribuida.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.LocalAtm
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.SouthWest
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carriel_cuenca_billeteradistribuida.model.Movimiento
import com.example.carriel_cuenca_billeteradistribuida.model.TipoOperacion
import com.example.carriel_cuenca_billeteradistribuida.ui.formatearFecha
import com.example.carriel_cuenca_billeteradistribuida.ui.formatearMonto
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.RojoEgreso
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.VerdeIngreso

// FILA DEL HISTORIAL: ícono, tipo, (categoría), detalle, fecha/hora y monto en verde o rojo.
@Composable
fun FilaMovimiento(movimiento: Movimiento) {
    // Color y signo según si el dinero entra o sale.
    val colorMonto = if (movimiento.tipo.esIngreso) VerdeIngreso else RojoEgreso
    val signo = if (movimiento.tipo.esIngreso) "+" else "-"

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Ícono dentro de un círculo del color del monto (transparente).
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(colorMonto.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(iconoDe(movimiento), contentDescription = movimiento.tipo.etiqueta, tint = colorMonto)
            }
            Spacer(Modifier.width(12.dp))
            // Columna central: tipo, categoría (solo servicios), detalle y fecha.
            Column(modifier = Modifier.weight(1f)) {
                Text(movimiento.tipo.etiqueta, fontWeight = FontWeight.SemiBold)
                // NULL SAFETY: "?.let" solo dibuja la categoría si NO es null ("Servicio Básico").
                movimiento.categoria?.let { categoria ->
                    Text(categoria, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                }
                Text(movimiento.detalle, color = Color.Gray, fontSize = 12.sp)
                Text(formatearFecha(movimiento.fechaHora), color = Color.Gray, fontSize = 12.sp)
            }
            // Monto con signo y color.
            Text("$signo${formatearMonto(movimiento.monto)}", color = colorMonto, fontWeight = FontWeight.Bold)
        }
    }
}

// Ícono de una fila del historial. En los pagos de servicio, el detalle empieza con el nombre
// del servicio ("AGUA - Contrato 778899"), así que tomamos la primera palabra.
fun iconoDe(movimiento: Movimiento): ImageVector =
    iconoDeTipo(movimiento.tipo, servicio = movimiento.detalle.substringBefore(" "))

// Ícono según el tipo de operación. Los pagos de servicio usan un ícono SEGÚN el servicio,
// así el "Servicio Básico" se distingue a simple vista. "servicio: String = """ = parámetro opcional.
fun iconoDeTipo(tipo: TipoOperacion, servicio: String = ""): ImageVector = when (tipo) {
    TipoOperacion.DEPOSITO -> Icons.Filled.Savings
    TipoOperacion.RETIRO -> Icons.Filled.LocalAtm
    TipoOperacion.TRANSFERENCIA_ENVIADA -> Icons.Filled.NorthEast
    TipoOperacion.TRANSFERENCIA_RECIBIDA -> Icons.Filled.SouthWest
    TipoOperacion.PAGO_SERVICIO -> iconoDeServicio(servicio)
}

// Ícono de cada servicio básico (también lo usa el diálogo de pago).
fun iconoDeServicio(servicio: String): ImageVector = when (servicio.uppercase()) {
    "LUZ" -> Icons.Filled.Bolt          // Rayo.
    "AGUA" -> Icons.Filled.WaterDrop    // Gota.
    "INTERNET" -> Icons.Filled.Wifi     // Wifi.
    else -> Icons.Filled.Receipt        // Factura genérica.
}
