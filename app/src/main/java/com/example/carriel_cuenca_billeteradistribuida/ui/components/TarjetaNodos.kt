// =====================================================================
//   Tarjeta que muestra cuántos teléfonos están conectados al servidor
//   (la ve el laboratorio) y el aviso que sale cuando no hay conexión.
// =====================================================================

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

// Tarjeta "Nodos conectados": cuántos teléfonos están conectados y de qué cuentas.
// Se actualiza sola cuando alguien entra o sale.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TarjetaNodos(nodos: NodosConectados?, enLinea: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),                                   // Crece/encoge suave al cambiar.
        shape = RoundedCornerShape(16.dp),
        // Fondo gris suave tomado de los colores de la app.
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {  // Margen interno de la tarjeta.
            // Fila de arriba: ícono, título y el número de nodos.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Hub, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                // El título ocupa el espacio libre y empuja el número a la derecha.
                Text("Nodos conectados", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                // El número grande. Si no hay conexión no se sabe, así que se muestra "—".
                Text(
                    text = if (enLinea && nodos != null) "${nodos.total}" else "—",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            // Debajo: la lista de cuentas conectadas, o el aviso "sin conexión".
            if (enLinea && nodos != null && nodos.cuentas.isNotEmpty()) {
                // Una píldora por cada cuenta; si no caben en una fila, pasan a la siguiente.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),  // Espacio entre píldoras de una misma fila.
                    verticalArrangement = Arrangement.spacedBy(6.dp),  // Espacio entre filas de píldoras.
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    nodos.cuentas.forEach { cuenta -> PildoraCuenta(cuenta) }  // Una píldora por cuenta.
                }
            } else if (!enLinea) {
                Text("Sin conexión con el servidor", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// Etiqueta de una cuenta conectada: punto verde + número de cuenta.
// Solo se usa dentro de este archivo (por eso es "private").
@Composable
private fun PildoraCuenta(cuenta: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))                       // Esquinas muy redondeadas: forma de píldora.
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(VerdeEnLinea))  // Punto verde.
        Spacer(Modifier.width(6.dp))
        Text(cuenta, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

// Aviso que aparece bajo los botones cuando no hay conexión,
// para que el usuario entienda por qué no puede operar.
@Composable
fun AvisoSinConexion(conectando: Boolean) {
    // Ámbar si está intentando reconectar; rojo si está desconectado.
    val color = if (conectando) AmbarConectando else RojoDesconectado
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.12f))              // El mismo color, casi transparente.
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(Icons.Filled.WifiOff, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        // Mensaje distinto según si está reconectando o totalmente desconectado.
        Text(
            if (conectando) "Reconectando… las operaciones se habilitan al volver la conexión."
            else "Sin conexión: puedes ver tu saldo e historial guardados, pero no operar.",
            fontSize = 12.sp
        )
    }
}
