// Paquete del panel de pruebas de concurrencia.
package com.example.carriel_cuenca_billeteradistribuida.ui.pruebas

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.carriel_cuenca_billeteradistribuida.model.TipoPrueba
import com.example.carriel_cuenca_billeteradistribuida.ui.BilleteraViewModel
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.AzulTarjeta
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.AzulTarjetaClaro
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.RojoEgreso
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.VerdeIngreso

// PANEL DE PRUEBAS DE CONCURRENCIA (diseño B: medidor arriba + resultado en recuadros).
// Comparte el MISMO BilleteraViewModel que el dashboard (misma cuenta y repositorio).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PruebasConcurrenciaScreen(
    viewModel: BilleteraViewModel,
    onVolver: () -> Unit,
    onActividad: () -> Unit
) {
    val pruebas by viewModel.pruebas.collectAsStateWithLifecycle()
    var numero by remember { mutableStateOf("50") }
    val n = numero.toIntOrNull() ?: 0
    var destino by remember { mutableStateOf("1001") }
    var montoTransf by remember { mutableStateOf("1") }
    val monto = montoTransf.replace(',', '.').toDoubleOrNull() ?: 0.0

    // RELOJ DE UI: sube en cada fotograma (~60/s) en el hilo main. Si el main se bloquea, se congela.
    var tic by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            androidx.compose.runtime.withFrameMillis { }
            tic++
        }
    }
    val transicion = rememberInfiniteTransition(label = "giro")
    val angulo by transicion.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart),
        label = "angulo"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pruebas de concurrencia") },
                navigationIcon = {
                    IconButton(onClick = onVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ---------- MEDIDOR "LA APP ESTÁ VIVA" ----------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(AzulTarjeta, AzulTarjetaClaro)))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Ícono que gira: si se detiene, la UI está congelada.
                Icon(
                    Icons.Filled.Autorenew, contentDescription = null, tint = Color.White,
                    modifier = Modifier.size(40.dp).rotate(angulo)
                )
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("La app está viva", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text("Reloj de la interfaz: $tic", color = Color.White.copy(alpha = 0.85f),
                        fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    Text("Si el giro se detiene, se congeló.", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
                }
            }

            // ---------- ENVIAR TRANSFERENCIAS (demo de dos pantallas) ----------
            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Enviar transferencias", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("En el teléfono de la cuenta destino se ven llegar en tiempo real.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = destino,
                            onValueChange = { destino = it.filter { c -> c.isDigit() }.take(10); onActividad() },
                            label = { Text("Cuenta") },
                            singleLine = true,
                            modifier = Modifier.weight(1.4f)
                        )
                        OutlinedTextField(
                            value = numero,
                            onValueChange = { numero = it.filter { c -> c.isDigit() }.take(3); onActividad() },
                            label = { Text("N.º") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = montoTransf,
                            onValueChange = { montoTransf = it; onActividad() },
                            label = { Text("c/u $") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Button(
                        onClick = { onActividad(); viewModel.lanzarTransferencias(n, destino, monto) },
                        enabled = n in 1..200 && destino.length >= 4 && monto > 0 && !pruebas.ejecutando,
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) { Text("Enviar $n transferencias", fontSize = 16.sp) }
                }
            }

            // ---------- RESULTADO EN RECUADROS ----------
            if (pruebas.total > 0) {
                Text("Resultado", fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Recuadro("${pruebas.completadas}/${pruebas.total}", "completadas", Modifier.weight(1f))
                    Recuadro(if (pruebas.ejecutando) "…" else "${pruebas.tiempoMs}", "ms en total", Modifier.weight(1f))
                }
                Spacer(Modifier.height(0.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Recuadro("${pruebas.hilos.size}", "hilos de fondo (IO)", Modifier.weight(1f))
                    Recuadro("0", "en el hilo main", Modifier.weight(1f), valorColor = VerdeIngreso)
                }
                if (pruebas.fallidas > 0) {
                    Text("Fallidas: ${pruebas.fallidas}", color = RojoEgreso, fontWeight = FontWeight.SemiBold)
                }
                // Detalle de hilos (plegable), para la evidencia.
                var verDetalle by remember { mutableStateOf(false) }
                OutlinedButton(onClick = { verDetalle = !verDetalle }) {
                    Text(if (verDetalle) "Ocultar detalle de hilos" else "Ver detalle de hilos")
                }
                if (verDetalle) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            pruebas.hilos.forEach { (hilo, veces) ->
                                Text("• $hilo → $veces", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                            }
                            if (pruebas.hiloRespuesta.isNotEmpty()) {
                                Text("Respuestas en el hilo: ${pruebas.hiloRespuesta}", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // ---------- AVANZADO: hilos puros y contraste ----------
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Avanzado", fontWeight = FontWeight.Bold)
                    Text("Probar solo los hilos, sin mover dinero:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { onActividad(); viewModel.lanzarConcurrentes(n, TipoPrueba.SERVIDOR) },
                            enabled = n in 1..200 && !pruebas.ejecutando,
                            modifier = Modifier.weight(1f)
                        ) { Text("$n al servidor") }
                        OutlinedButton(
                            onClick = { onActividad(); viewModel.lanzarConcurrentes(n, TipoPrueba.ROOM) },
                            enabled = n in 1..200 && !pruebas.ejecutando,
                            modifier = Modifier.weight(1f)
                        ) { Text("$n a Room") }
                    }
                    Text("Contraste: así se ve si el trabajo pesado va en el hilo main (el medidor de arriba se congela 3 s).",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(
                        onClick = { viewModel.bloquearMainAPosta(3) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RojoEgreso),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Bloquear el hilo main 3 s (MAL) ⚠") }
                }
            }
        }
    }
}

// Un recuadro grande con un número y su etiqueta (los 4 resultados).
@Composable
private fun Recuadro(valor: String, etiqueta: String, modifier: Modifier = Modifier, valorColor: Color? = null) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(valor, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace,
                color = valorColor ?: MaterialTheme.colorScheme.onSurface)
            Text(etiqueta, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
