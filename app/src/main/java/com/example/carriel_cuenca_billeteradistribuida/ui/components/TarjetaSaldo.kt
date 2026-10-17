// Paquete de componentes reutilizables.
package com.example.carriel_cuenca_billeteradistribuida.ui.components

import androidx.compose.animation.core.animateFloatAsState      // Anima un número de un valor a otro.
import androidx.compose.animation.core.tween                    // Animación de duración fija.
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carriel_cuenca_billeteradistribuida.model.EstadoConexion
import com.example.carriel_cuenca_billeteradistribuida.ui.formatearMonto
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.AzulTarjeta
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.AzulTarjetaClaro

// TARJETA DE SALDO (parte superior): saldo en números grandes, cuenta e indicador del WebSocket.
@Composable
fun TarjetaSaldo(
    saldo: Double?,                     // null = todavía no se conoce el saldo.
    cuenta: String,                     // Número de cuenta de la app.
    estadoConexion: EstadoConexion
) {
    // SALDO ANIMADO: cuando el saldo cambia (ej. llega una transferencia), el número
    // "cuenta" desde el valor anterior hasta el nuevo durante 800 ms.
    // animateFloatAsState devuelve un estado que Compose redibuja en cada fotograma de la animación.
    val saldoAnimado by animateFloatAsState(
        targetValue = (saldo ?: 0.0).toFloat(),
        animationSpec = tween(durationMillis = 800),
        label = "saldo"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        // Fondo con DEGRADADO azul en diagonal.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(AzulTarjeta, AzulTarjetaClaro)))
                .padding(24.dp)
        ) {
            // Fila superior: título a la izquierda, indicador a la derecha.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Saldo actual", color = Color.White.copy(alpha = 0.8f))
                EstadoConexionChip(estadoConexion)
            }
            Spacer(Modifier.height(12.dp))
            // Saldo en NÚMEROS GRANDES. Si es null: "$ --".
            Text(
                text = if (saldo == null) "$ --" else formatearMonto(saldoAnimado.toDouble()),
                color = Color.White,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text("Cuenta N.º $cuenta", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
        }
    }
}
