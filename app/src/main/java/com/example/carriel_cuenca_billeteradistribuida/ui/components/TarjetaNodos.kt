// Paquete de componentes reutilizables.
package com.example.carriel_cuenca_billeteradistribuida.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow                   // Fila que salta de línea si no cabe.
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carriel_cuenca_billeteradistribuida.model.NodosConectados
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.AmbarConectando
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.RojoDesconectado
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.VerdeEnLinea

// TARJETA "NODOS CONECTADOS" (solo laboratorio): cuántos teléfonos tienen el WebSocket abierto
// con el servidor ahora mismo, y de qué cuentas. Se actualiza sola cuando alguien entra o sale.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TarjetaNodos(nodos: NodosConectados?, enLinea: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),                                   // Crece/encoge suave al cambiar.
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Hub, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Nodos conectados", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                // El número grande. Sin túnel no se sabe: "—".
                Text(
                    text = if (enLinea && nodos != null) "${nodos.total}" else "—",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            if (enLinea && nodos != null && nodos.cuentas.isNotEmpty()) {
                // Una "píldora" con punto verde por cada cuenta conectada.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    nodos.cuentas.forEach { cuenta -> PildoraCuenta(cuenta) }
                }
            } else if (!enLinea) {
                Text("Sin conexión con el servidor", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PildoraCuenta(cuenta: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(VerdeEnLinea))
        Spacer(Modifier.width(6.dp))
        Text(cuenta, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

// AVISO bajo los botones cuando no hay conexión: explica por qué están bloqueados.
@Composable
fun AvisoSinConexion(conectando: Boolean) {
    val color = if (conectando) AmbarConectando else RojoDesconectado
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(Icons.Filled.WifiOff, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            if (conectando) "Reconectando… las operaciones se habilitan al volver la conexión."
            else "Sin conexión: puedes ver tu saldo e historial guardados, pero no operar.",
            fontSize = 12.sp
        )
    }
}
