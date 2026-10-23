// Paquete de componentes reutilizables.
package com.example.carriel_cuenca_billeteradistribuida.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalAtm
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.carriel_cuenca_billeteradistribuida.model.TipoOperacion

// PANEL DE ACCIONES: grid de 2 x 2 con los 4 botones grandes.
// "habilitado = false" mientras hay una operación en curso (evita enviar dos veces).
// "onAccionClick" es una FUNCIÓN que recibe el tipo de operación: el panel NO hace la operación,
// solo AVISA a la pantalla qué botón se tocó (la pantalla abre el diálogo correspondiente).
@Composable
fun PanelAcciones(habilitado: Boolean, onAccionClick: (TipoOperacion) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {  // Columna con 2 filas separadas 12 dp.
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {  // Primera fila: depositar y retirar.
            // "Modifier.weight(1f)" = cada botón ocupa la mitad del ancho.
            // Las llaves { } del final son el onClick del botón (lambda escrita fuera de los paréntesis).
            BotonAccion("Depositar", Icons.Filled.Savings, habilitado, Modifier.weight(1f)) {
                onAccionClick(TipoOperacion.DEPOSITO)
            }
            BotonAccion("Retirar", Icons.Filled.LocalAtm, habilitado, Modifier.weight(1f)) {
                onAccionClick(TipoOperacion.RETIRO)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {  // Segunda fila: transferir y pagar servicios.
            BotonAccion("Transferir", Icons.Filled.SwapHoriz, habilitado, Modifier.weight(1f)) {
                onAccionClick(TipoOperacion.TRANSFERENCIA_ENVIADA)
            }
            BotonAccion("Pagar Servicios", Icons.Filled.Receipt, habilitado, Modifier.weight(1f)) {
                onAccionClick(TipoOperacion.PAGO_SERVICIO)
            }
        }
    }
}

// Un botón grande con ícono y texto. El último parámetro es una lambda (se puede escribir fuera de los paréntesis).
@Composable
fun BotonAccion(
    texto: String,                                              // Texto del botón.
    icono: ImageVector,                                         // Ícono (ImageVector = ícono dibujado con vectores).
    habilitado: Boolean,                                        // false = botón bloqueado.
    modifier: Modifier = Modifier,                              // Modifier opcional que manda quien lo llama (ej. weight).
    onClick: () -> Unit                                         // Qué hacer al tocarlo.
) {
    Card(
        onClick = onClick,                                      // Card con onClick: una tarjeta que se toca como un botón.
        enabled = habilitado,                                   // Desactivado = gris y sin responder.
        modifier = modifier.height(64.dp),                      // Más bajo: ocupa menos pantalla.
        shape = RoundedCornerShape(16.dp),
        // Color de fondo del tema (secondaryContainer).
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        // Ícono y texto EN FILA (antes en columna): la tarjeta queda compacta.
        Row(
            modifier = Modifier
                .fillMaxSize()                                  // Ocupa toda la tarjeta.
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,         // Centra horizontalmente.
            verticalAlignment = Alignment.CenterVertically      // Centra verticalmente.
        ) {
            Icon(icono, contentDescription = texto, modifier = Modifier.size(22.dp))  // contentDescription: lo lee el lector de pantalla.
            Spacer(Modifier.width(8.dp))
            Text(texto, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
}
