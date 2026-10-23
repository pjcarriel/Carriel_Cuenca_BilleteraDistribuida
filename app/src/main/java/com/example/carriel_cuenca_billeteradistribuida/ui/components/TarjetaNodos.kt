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
// @OptIn: FlowRow es una API EXPERIMENTAL de Compose; con esta anotación aceptamos usarla.
// Parámetros: "nodos" (null = aún no llega la lista del servidor) y "enLinea" (WebSocket abierto).
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TarjetaNodos(nodos: NodosConectados?, enLinea: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),                                   // Crece/encoge suave al cambiar.
        shape = RoundedCornerShape(16.dp),
        // Fondo de la tarjeta tomado del tema (colorScheme): un gris suave.
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {  // Margen interno de la tarjeta.
            // Fila de arriba: ícono, título y el número de nodos.
            Row(verticalAlignment = Alignment.CenterVertically) {
                // contentDescription = null: ícono decorativo (el lector de pantalla lo ignora).
                Icon(Icons.Filled.Hub, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                // weight(1f): el título ocupa el espacio libre y empuja el número a la derecha.
                Text("Nodos conectados", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                // El número grande. Sin túnel no se sabe: "—".
                Text(
                    text = if (enLinea && nodos != null) "${nodos.total}" else "—",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            // Debajo: las cuentas conectadas (si hay túnel y la lista no está vacía) o el aviso "sin conexión".
            if (enLinea && nodos != null && nodos.cuentas.isNotEmpty()) {
                // Una "píldora" con punto verde por cada cuenta conectada.
                FlowRow(                                        // Como Row, pero salta de línea si no cabe.
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

// PÍLDORA de una cuenta: punto verde + número de cuenta. "private" = solo se usa en este archivo.
@Composable
private fun PildoraCuenta(cuenta: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))                       // RoundedCornerShape(50) = esquinas al 50 %: forma de píldora.
            .background(MaterialTheme.colorScheme.surface)      // Fondo del color "surface" del tema.
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(VerdeEnLinea))  // Punto verde de 8 dp.
        Spacer(Modifier.width(6.dp))
        Text(cuenta, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

// AVISO bajo los botones cuando no hay conexión: explica por qué están bloqueados.
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
        // if/else como EXPRESIÓN: elige cuál de los dos textos se muestra.
        Text(
            if (conectando) "Reconectando… las operaciones se habilitan al volver la conexión."
            else "Sin conexión: puedes ver tu saldo e historial guardados, pero no operar.",
            fontSize = 12.sp
        )
    }
}
