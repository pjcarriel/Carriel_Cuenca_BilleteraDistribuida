// Paquete de componentes reutilizables.
package com.example.carriel_cuenca_billeteradistribuida.ui.components

import androidx.compose.animation.animateContentSize           // Anima el cambio de tamaño de la tarjeta.
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextAlign
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
// Al TOCARLA se DESPLIEGA la descripción de la operación y un botón "Ver comprobante";
// al tocarla otra vez se vuelve a plegar.
// @Composable = función que DIBUJA interfaz; Compose la vuelve a llamar cuando cambian sus datos.
@Composable
fun FilaMovimiento(movimiento: Movimiento, onVerComprobante: () -> Unit = {}) {
    // Color y signo según si el dinero entra o sale.
    val colorMonto = if (movimiento.tipo.esIngreso) VerdeIngreso else RojoEgreso
    val signo = if (movimiento.tipo.esIngreso) "+" else "-"
    // ¿Está desplegada? rememberSaveable(id): cada movimiento recuerda lo suyo (también al rotar).
    var desplegada by rememberSaveable(movimiento.id) { mutableStateOf(false) }

    // Card con onClick: toda la tarjeta se puede TOCAR (con efecto de onda) para desplegarla o plegarla.
    // animateContentSize: la tarjeta crece y se encoge con una animación suave.
    Card(
        onClick = { desplegada = !desplegada },
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp)
    ) {
      Column {
        Row(
            // Modifier: cadena de ajustes (tamaño, márgenes, fondo...) que se aplican EN ORDEN.
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
                    .background(colorMonto.copy(alpha = 0.12f)),  // alpha 0.12 = casi transparente.
                contentAlignment = Alignment.Center
            ) {
                Icon(iconoDe(movimiento), contentDescription = movimiento.tipo.etiqueta, tint = colorMonto)
            }
            Spacer(Modifier.width(12.dp))                       // Espacio entre el ícono y los textos.
            // Columna central: tipo, categoría (solo servicios), detalle y fecha.
            Column(modifier = Modifier.weight(1f)) {            // weight(1f): ocupa todo el espacio que sobra.
                Text(movimiento.tipo.etiqueta, fontWeight = FontWeight.SemiBold)
                // NULL SAFETY: "?.let" solo dibuja la categoría si NO es null ("Servicio Básico").
                movimiento.categoria?.let { categoria ->
                    Text(categoria, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                }
                Text(movimiento.detalle, color = Color.Gray, fontSize = 12.sp)
                Text(formatearFecha(movimiento.fechaHora), color = Color.Gray, fontSize = 12.sp)
            }
            // Monto con signo y color, y debajo una flecha que indica si se puede desplegar o plegar.
            Column(horizontalAlignment = Alignment.End) {
                Text("$signo${formatearMonto(movimiento.monto)}", color = colorMonto, fontWeight = FontWeight.Bold)
                Icon(
                    if (desplegada) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (desplegada) "Ocultar descripción" else "Ver descripción",
                    tint = Color.Gray
                )
            }
        }

        // ---------- DESCRIPCIÓN (solo si está desplegada) ----------
        if (desplegada) {
            Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                DatoDescripcion("Descripción", movimiento.detalle)
                DatoDescripcion("Operación", movimiento.tipo.etiqueta)
                DatoDescripcion("Fecha y hora", formatearFecha(movimiento.fechaHora))
                DatoDescripcion("N.º de comprobante", numeroComprobante(movimiento))
                DatoDescripcion("Estado", "Exitosa")
                Spacer(Modifier.height(8.dp))
                // Botón que abre el comprobante completo (el que se puede compartir).
                Button(onClick = onVerComprobante, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Receipt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Ver comprobante")
                }
            }
        }
      }
    }
}

// Una línea de la descripción desplegada: etiqueta gris a la izquierda y valor a la derecha.
@Composable
private fun DatoDescripcion(etiqueta: String, valor: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(etiqueta, color = Color.Gray, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(valor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1.4f),
            textAlign = TextAlign.End)
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
