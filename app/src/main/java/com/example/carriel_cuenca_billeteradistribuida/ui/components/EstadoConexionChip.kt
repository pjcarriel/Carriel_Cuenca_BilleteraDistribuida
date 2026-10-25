// =====================================================================
//   La píldora de arriba que dice si el teléfono está conectado al servidor:
//   verde "En línea", ámbar "Conectando..." o rojo "Desconectado".
// =====================================================================

package com.example.carriel_cuenca_billeteradistribuida.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carriel_cuenca_billeteradistribuida.model.EstadoConexion
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.AmbarConectando
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.RojoDesconectado
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.VerdeEnLinea

// Muestra el estado de la conexión en tiempo real (WebSocket) con el servidor.
// Ojo: solo dice si el "canal en vivo" está abierto, no si se pueden hacer operaciones.
@Composable
fun EstadoConexionChip(estado: EstadoConexion) {
    // Según el estado, elegimos un color y un texto.
    val (color, texto) = when (estado) {
        EstadoConexion.EN_LINEA -> VerdeEnLinea to "En línea"
        EstadoConexion.CONECTANDO -> AmbarConectando to "Conectando..."
        EstadoConexion.DESCONECTADO -> RojoDesconectado to "Desconectado"
    }
    // La píldora: fondo blanco transparente con el círculo y el texto adentro.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))                    // Forma de píldora.
            .background(Color.White.copy(alpha = 0.15f))     // Fondo translúcido.
            .padding(horizontal = 10.dp, vertical = 4.dp)       // Margen interno.
    ) {
        // El círculo de color.
        Box(
            modifier = Modifier
                .size(10.dp)                                    // Círculo de 10 dp.
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(6.dp))                            // Espacio entre el círculo y el texto.
        Text(texto, color = Color.White, fontSize = 12.sp)
    }
}
