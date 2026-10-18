// Paquete del panel de pruebas de concurrencia.
package com.example.carriel_cuenca_billeteradistribuida.ui.pruebas

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.carriel_cuenca_billeteradistribuida.model.TipoPrueba
import com.example.carriel_cuenca_billeteradistribuida.ui.BilleteraViewModel
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.RojoEgreso
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.VerdeIngreso

// PANTALLA DE PRUEBAS DE CONCURRENCIA: demuestra en vivo el manejo de hilos.
// Comparte el MISMO BilleteraViewModel que el dashboard (misma cuenta y repositorio).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PruebasConcurrenciaScreen(
    viewModel: BilleteraViewModel,
    onVolver: () -> Unit,
    onActividad: () -> Unit
) {
    val pruebas by viewModel.pruebas.collectAsStateWithLifecycle()
    // Campo de texto con el número de peticiones (por defecto 50).
    var numero by remember { mutableStateOf("50") }
    val n = numero.toIntOrNull() ?: 0
    // Para la demo de dos pantallas: a qué cuenta se transfiere y cuánto.
    var destino by remember { mutableStateOf("1001") }
    var montoTransf by remember { mutableStateOf("1") }
    val montoValido = montoTransf.replace(',', '.').toDoubleOrNull() ?: 0.0

    // RELOJ DE UI: se incrementa en CADA fotograma (~60 por segundo) en el hilo main.
    // Si el hilo main se bloquea, este número se CONGELA: es la prueba visual de "cero ANR".
    var tic by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            androidx.compose.runtime.withFrameMillis { }   // Espera al siguiente fotograma (no bloquea).
            tic++
        }
    }
    // Ícono que gira sin parar; también se congela si el hilo main se bloquea.
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ---------- RELOJ DE UI (latido) ----------
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Autorenew, contentDescription = null, modifier = Modifier.size(32.dp).rotate(angulo))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Reloj de la interfaz (hilo main)", fontWeight = FontWeight.SemiBold)
                        Text("Fotogramas: $tic", fontFamily = FontFamily.Monospace)
                        Text("Si este número y el ícono se detienen, la UI está congelada.", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }

            // ---------- CONTROL ----------
            OutlinedTextField(
                value = numero,
                onValueChange = { numero = it.filter { c -> c.isDigit() }.take(3); onActividad() },
                label = { Text("Número de peticiones simultáneas") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // ---------- DEMO DE DOS PANTALLAS: enviar N transferencias a una cuenta ----------
            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Enviar transferencias a una cuenta", fontWeight = FontWeight.Bold)
                    Text("Lanza $n transferencias simultáneas; en el teléfono de la cuenta destino se ven llegar en tiempo real.", fontSize = 12.sp, color = Color.Gray)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = destino,
                            onValueChange = { destino = it.filter { c -> c.isDigit() }.take(10); onActividad() },
                            label = { Text("Cuenta destino") },
                            singleLine = true,
                            modifier = Modifier.weight(1.3f)
                        )
                        OutlinedTextField(
                            value = montoTransf,
                            onValueChange = { montoTransf = it; onActividad() },
                            label = { Text("Monto c/u") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Button(
                        onClick = { onActividad(); viewModel.lanzarTransferencias(n, destino, montoValido) },
                        enabled = n in 1..200 && destino.length >= 4 && montoValido > 0 && !pruebas.ejecutando,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Enviar $n transferencias a $destino") }
                }
            }

            // ---------- PRUEBAS DE HILOS PUROS (sin mover dinero) ----------
            Text("O probar solo los hilos (sin mover dinero):", fontSize = 13.sp, color = Color.Gray)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { onActividad(); viewModel.lanzarConcurrentes(n, TipoPrueba.SERVIDOR) },
                    enabled = n in 1..200 && !pruebas.ejecutando,
                    modifier = Modifier.weight(1f)
                ) { Text("$n al servidor") }
                Button(
                    onClick = { onActividad(); viewModel.lanzarConcurrentes(n, TipoPrueba.ROOM) },
                    enabled = n in 1..200 && !pruebas.ejecutando,
                    modifier = Modifier.weight(1f)
                ) { Text("$n a Room") }
            }

            // ---------- RESULTADOS ----------
            if (pruebas.total > 0) {
                Card {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Progreso: ${pruebas.completadas} / ${pruebas.total}", fontWeight = FontWeight.Bold)
                            if (pruebas.ejecutando) {
                                Spacer(Modifier.width(10.dp))
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            }
                        }
                        LinearProgressIndicator(
                            progress = { if (pruebas.total == 0) 0f else pruebas.completadas.toFloat() / pruebas.total },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (!pruebas.ejecutando) {
                            Text("Terminadas en ${pruebas.tiempoMs} ms", color = VerdeIngreso, fontWeight = FontWeight.SemiBold)
                        }
                        if (pruebas.fallidas > 0) {
                            Text("Fallidas: ${pruebas.fallidas}", color = RojoEgreso, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("Hilos de fondo (Dispatchers.IO) que atendieron las peticiones:", fontWeight = FontWeight.SemiBold)
                        // Lista de hilos IO y cuántas peticiones atendió cada uno.
                        pruebas.hilos.forEach { (hilo, veces) ->
                            Text("• $hilo  →  $veces", fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                        }
                        Text("Total de hilos IO distintos: ${pruebas.hilos.size}", color = MaterialTheme.colorScheme.primary)
                        if (pruebas.hiloRespuesta.isNotEmpty()) {
                            Text("Respuestas procesadas en el hilo: ${pruebas.hiloRespuesta}", fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                        }
                        Text("Operaciones de red/BD en el hilo main: 0", color = VerdeIngreso, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // ---------- CONTRASTE: lo que NO se debe hacer ----------
            Card(colors = CardDefaults.cardColors(containerColor = RojoEgreso.copy(alpha = 0.08f))) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Contraste: trabajo pesado en el hilo main", fontWeight = FontWeight.Bold, color = RojoEgreso)
                    Text("Al pulsar, el hilo main se bloquea 3 segundos a propósito. El reloj de arriba se CONGELARÁ: por eso el trabajo pesado nunca va en main.", fontSize = 12.sp)
                    OutlinedButton(onClick = { viewModel.bloquearMainAPosta(3) }) {
                        Text("Bloquear el hilo main 3 s (MAL)")
                    }
                }
            }

            Text(
                "Mientras lanzas peticiones, observa que el reloj de arriba NO se detiene: la red y la base de datos corren en Dispatchers.IO y el hilo main queda libre para la interfaz.",
                fontSize = 12.sp, color = Color.Gray
            )
        }
    }
}
