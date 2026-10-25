// =====================================================================
//   Cada fila del historial (ícono, tipo, fecha y monto en verde o rojo)
//   y los íconos de cada operación y de cada servicio (luz, agua...).
// =====================================================================

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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocalAtm
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Recycling
import androidx.compose.material.icons.filled.Tv
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

// Una fila del historial. Al tocarla se abre el panel de detalle.
@Composable
fun FilaMovimiento(movimiento: Movimiento, onClick: () -> Unit = {}) {   // onClick: abre el panel de detalle.
    // Color y signo según si el dinero entra o sale.
    val colorMonto = if (movimiento.tipo.esIngreso) VerdeIngreso else RojoEgreso
    val signo = if (movimiento.tipo.esIngreso) "+" else "-"

    // Toda la tarjeta se puede tocar.
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()                                 // Ocupa todo el ancho.
                .padding(12.dp),                                // Margen interno de 12 dp.
            verticalAlignment = Alignment.CenterVertically      // Centra verticalmente lo de la fila.
        ) {
            // Ícono dentro de un círculo del color del monto (transparente).
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)                          // Recorta en forma de círculo.
                    .background(colorMonto.copy(alpha = 0.12f)),  // Mismo color, casi transparente.
                contentAlignment = Alignment.Center
            ) {
                Icon(iconoDe(movimiento), contentDescription = movimiento.tipo.etiqueta, tint = colorMonto)
            }
            Spacer(Modifier.width(12.dp))                       // Espacio entre el ícono y los textos.
            // Columna central: tipo, categoría (solo servicios), detalle y fecha.
            Column(modifier = Modifier.weight(1f)) {            // Ocupa todo el espacio que sobra.
                Text(movimiento.tipo.etiqueta, fontWeight = FontWeight.SemiBold)
                // La categoría solo se muestra si existe (los pagos de servicio la tienen).
                movimiento.categoria?.let { categoria ->
                    Text(categoria, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                }
                Text(movimiento.detalle, color = Color.Gray, fontSize = 12.sp)
                Text(formatearFecha(movimiento.fechaHora), color = Color.Gray, fontSize = 12.sp)
            }
            // Monto con signo y color.
            Text("$signo${formatearMonto(movimiento.monto)}", color = colorMonto, fontWeight = FontWeight.Bold)
            // Flechita ">": indica que al tocar se abre el detalle.
            Icon(Icons.Filled.ChevronRight, contentDescription = "Ver detalle", tint = Color.Gray)
        }
    }
}

// Elige el ícono de un movimiento del historial. En los pagos de servicio el detalle
// empieza con el nombre del servicio ("AGUA - Contrato 778899"), así que se toma la primera palabra.
fun iconoDe(movimiento: Movimiento): ImageVector =
    iconoDeTipo(movimiento.tipo, servicio = movimiento.detalle.substringBefore(" "))

// Elige el ícono según el tipo de operación. Los pagos de servicio llevan el ícono
// del servicio pagado (gota para agua, rayo para luz...) para reconocerlos rápido.
fun iconoDeTipo(tipo: TipoOperacion, servicio: String = ""): ImageVector = when (tipo) {
    TipoOperacion.DEPOSITO -> Icons.Filled.Savings
    TipoOperacion.RETIRO -> Icons.Filled.LocalAtm
    TipoOperacion.TRANSFERENCIA_ENVIADA -> Icons.Filled.NorthEast
    TipoOperacion.TRANSFERENCIA_RECIBIDA -> Icons.Filled.SouthWest
    TipoOperacion.PAGO_SERVICIO -> iconoDeServicio(servicio)
}

// Ícono de cada servicio: luz, agua e internet fijos; los demás, del catálogo del servidor.
fun iconoDeServicio(servicio: String): ImageVector = when (servicio.uppercase()) {
    "LUZ" -> Icons.Filled.Bolt          // Rayo.
    "AGUA" -> Icons.Filled.WaterDrop    // Gota.
    "INTERNET" -> Icons.Filled.Wifi     // Wifi.
    else -> iconoPorClave(CatalogoServicios.iconoDe(servicio.uppercase()))
}

// Claves de ícono que puede elegir el laboratorio al crear un servicio (las mismas que acepta el servidor).
val CLAVES_ICONO = listOf("RAYO", "GOTA", "WIFI", "TELEFONO", "GAS", "TV", "BASURA", "OTRO")

// Convierte una clave de texto ("GOTA", "RAYO"...) en el dibujo del ícono.
fun iconoPorClave(clave: String?): ImageVector = when (clave) {
    "RAYO" -> Icons.Filled.Bolt
    "GOTA" -> Icons.Filled.WaterDrop
    "WIFI" -> Icons.Filled.Wifi
    "TELEFONO" -> Icons.Filled.Phone
    "GAS" -> Icons.Filled.LocalFireDepartment
    "TV" -> Icons.Filled.Tv
    "BASURA" -> Icons.Filled.Recycling
    else -> Icons.Filled.Receipt        // OTRO o desconocido: factura genérica.
}

// CATÁLOGO DE SERVICIOS: guarda qué ícono tiene cada servicio descargado del servidor.
object CatalogoServicios {
    // @Volatile: asegura que todas las partes de la app vean siempre la versión más nueva.
    @Volatile
    private var iconos: Map<String, String> = emptyMap()

    // Guarda la lista nueva de servicios descargada del servidor.
    fun actualizar(servicios: List<com.example.carriel_cuenca_billeteradistribuida.model.Servicio>) {
        iconos = servicios.associate { it.codigo to it.icono }
    }

    // Devuelve la clave del ícono de un servicio (o null si no se conoce).
    fun iconoDe(codigo: String): String? = iconos[codigo]
}
