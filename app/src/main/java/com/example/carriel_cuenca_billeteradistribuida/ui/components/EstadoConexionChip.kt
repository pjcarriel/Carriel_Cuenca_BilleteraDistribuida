// Paquete de COMPONENTES: piezas pequeñas y reutilizables de la interfaz.
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

// INDICADOR DEL WEBSOCKET: verde "En línea", ámbar "Conectando..." o rojo "Desconectado".
// Indica si el TÚNEL de tiempo real está abierto (no si se puede operar por REST).
@Composable
fun EstadoConexionChip(estado: EstadoConexion) {
    // "when" como EXPRESIÓN que devuelve un PAR (color, texto); "val (a, b) =" lo separa.
    // Como EstadoConexion es un enum, Kotlin obliga a cubrir los tres casos.
    val (color, texto) = when (estado) {
        EstadoConexion.EN_LINEA -> VerdeEnLinea to "En línea"
        EstadoConexion.CONECTANDO -> AmbarConectando to "Conectando..."
        EstadoConexion.DESCONECTADO -> RojoDesconectado to "Desconectado"
    }
    // Una "píldora" blanca semitransparente que contiene el círculo y el texto.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))                    // Forma de píldora.
            .background(Color.White.copy(alpha = 0.15f))     // Fondo translúcido.
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        // El círculo de color.
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text(texto, color = Color.White, fontSize = 12.sp)
    }
}
