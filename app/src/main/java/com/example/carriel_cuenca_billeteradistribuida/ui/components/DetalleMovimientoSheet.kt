// =====================================================================
//   El panel que sube desde abajo al tocar un movimiento del historial:
//   muestra sus datos y el botón "Ver comprobante".
// =====================================================================

package com.example.carriel_cuenca_billeteradistribuida.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet           // Panel modal que sube desde abajo.
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carriel_cuenca_billeteradistribuida.model.Movimiento
import com.example.carriel_cuenca_billeteradistribuida.ui.formatearFecha
import com.example.carriel_cuenca_billeteradistribuida.ui.formatearMonto
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.RojoEgreso
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.VerdeIngreso

// Panel de detalle de un movimiento, con el botón "Ver comprobante".
// Se cierra deslizándolo hacia abajo o tocando fuera.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetalleMovimientoSheet(
    movimiento: Movimiento,
    onVerComprobante: () -> Unit,       // Abre el comprobante completo (el que se comparte).
    onCerrar: () -> Unit
) {
    // Verde y "+" si el dinero entra; rojo y "-" si sale.
    val colorMonto = if (movimiento.tipo.esIngreso) VerdeIngreso else RojoEgreso
    val signo = if (movimiento.tipo.esIngreso) "+" else "-"
    // El panel se abre completo de una vez (no a la mitad).
    val estadoPanel = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onCerrar, sheetState = estadoPanel) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
        ) {
            // Ícono de la operación en un círculo del color del monto.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(colorMonto.copy(alpha = 0.12f))
            ) { Icon(iconoDe(movimiento), contentDescription = null, tint = colorMonto, modifier = Modifier.size(28.dp)) }
            Spacer(Modifier.height(10.dp))
            Text(movimiento.tipo.etiqueta, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text("$signo${formatearMonto(movimiento.monto)}", color = colorMonto, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp)
            Spacer(Modifier.height(14.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            // Descripción de la operación, en filas "etiqueta: valor".
            DatoDetalle("Descripción", movimiento.detalle)
            movimiento.categoria?.let { DatoDetalle("Categoría", it) }      // Solo pagos de servicio.
            DatoDetalle("Fecha y hora", formatearFecha(movimiento.fechaHora))
            DatoDetalle("N.º de comprobante", numeroComprobante(movimiento))
            DatoDetalle("Estado", "Exitosa")

            Spacer(Modifier.height(18.dp))
            // Botón que abre el comprobante completo (con la opción de compartirlo).
            Button(onClick = onVerComprobante, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Receipt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Ver comprobante")
            }
        }
    }
}

// Una línea del detalle: etiqueta gris a la izquierda y valor a la derecha.
@Composable
private fun DatoDetalle(etiqueta: String, valor: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(etiqueta, color = Color.Gray, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(valor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.weight(1.4f))
    }
}
