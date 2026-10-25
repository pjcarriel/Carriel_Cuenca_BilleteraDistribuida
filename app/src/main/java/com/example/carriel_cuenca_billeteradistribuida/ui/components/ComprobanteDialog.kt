// =====================================================================
//   La ventana con el COMPROBANTE de una operación, como el de un banco.
//   Se puede descargar a la Galería o compartir como imagen.
// =====================================================================

package com.example.carriel_cuenca_billeteradistribuida.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.material.icons.filled.Download
import androidx.core.content.ContextCompat
import com.example.carriel_cuenca_billeteradistribuida.util.ALBUM_COMPROBANTES
import com.example.carriel_cuenca_billeteradistribuida.util.guardarEnGaleria
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer     // "Capa" donde se graba lo dibujado (para la imagen).
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.carriel_cuenca_billeteradistribuida.R
import com.example.carriel_cuenca_billeteradistribuida.model.Movimiento
import com.example.carriel_cuenca_billeteradistribuida.ui.formatearFecha
import com.example.carriel_cuenca_billeteradistribuida.ui.formatearMonto
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.AzulTarjeta
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.AzulTarjetaClaro
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.RojoEgreso
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.VerdeEnLinea
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.VerdeIngreso
import com.example.carriel_cuenca_billeteradistribuida.util.compartirImagen
import kotlinx.coroutines.launch

// Colores FIJOS del comprobante: siempre fondo blanco y texto oscuro (también en modo oscuro),
// para que la imagen compartida se vea igual que un comprobante de banco en cualquier teléfono.
private val TextoOscuro = Color(0xFF1F2937)
private val TextoGris = Color(0xFF6B7280)

// Número del comprobante (ej. 00000123). Si el servidor no le dio número, usa el del teléfono con "L".
fun numeroComprobante(movimiento: Movimiento): String =
    movimiento.transaccionId?.let { "%08d".format(it) } ?: "L-${movimiento.id}"

// Ventana del comprobante de una operación, con botones para descargarlo y compartirlo.
// Los datos salen de la base de datos del teléfono, así que funciona aunque no haya internet.
@Composable
fun ComprobanteDialog(
    movimiento: Movimiento,         // La operación del comprobante.
    cuenta: String,                 // Cuenta del usuario (ej. "1001").
    titular: String,                // Nombre del usuario (ej. "Pamela Carriel").
    onCerrar: () -> Unit
) {
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()                      // Permite hacer tareas en segundo plano.
    var compartiendo by remember { mutableStateOf(false) }      // true mientras se genera la imagen.
    var descargando by remember { mutableStateOf(false) }       // true mientras se guarda en la Galería.

    // "Foto" del comprobante: todo lo que se dibuja en el comprobante se copia aquí,
    // y luego se convierte en una imagen para descargarla o compartirla.
    val capa = rememberGraphicsLayer()

    // DESCARGAR: convierte el comprobante en imagen y la guarda en la Galería.
    // Al final sale un mensajito flotante (Toast) diciendo si se guardó o si falló.
    fun descargar() {
        descargando = true
        alcance.launch {
            val imagen = capa.toImageBitmap().asAndroidBitmap()
            val ok = guardarEnGaleria(contexto, imagen, "comprobante_${numeroComprobante(movimiento)}")
            Toast.makeText(
                contexto,
                if (ok) "Comprobante guardado en Galería › $ALBUM_COMPROBANTES" else "No se pudo guardar el comprobante",
                Toast.LENGTH_LONG
            ).show()
            descargando = false
        }
    }

    // Solo en Android 8 y 9: pide permiso para guardar archivos; si lo dan, descarga.
    val pedirPermiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        if (concedido) descargar()
        else Toast.makeText(contexto, "Sin el permiso de almacenamiento no se puede descargar", Toast.LENGTH_LONG).show()
    }

    // Verde si el dinero entra, rojo si sale.
    val esIngreso = movimiento.tipo.esIngreso
    val colorMonto = if (esIngreso) VerdeIngreso else RojoEgreso

    // La ventana puede ser más ancha que una ventana normal.
    Dialog(onDismissRequest = onCerrar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())          // En pantallas pequeñas se puede desplazar.
        ) {
            // ---------- EL COMPROBANTE (esta parte es la que se convierte en imagen) ----------
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // Copia el comprobante en la "foto" y la muestra en pantalla.
                    // Así lo que se ve y lo que se comparte son exactamente lo mismo.
                    .drawWithContent {
                        capa.record { this@drawWithContent.drawContent() }
                        drawLayer(capa)
                    }
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White)
            ) {
                // Encabezado azul con el logo de la app.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.linearGradient(listOf(AzulTarjeta, AzulTarjetaClaro)))
                        .padding(16.dp)
                ) {
                    // Logo de la app (billetera con tarjetas) sobre un círculo azul.
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(AzulTarjeta, AzulTarjetaClaro)))
                    ) {
                        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Billetera CC", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Comprobante de operación", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    // Sello de éxito.
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = VerdeEnLinea, modifier = Modifier.size(52.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (esIngreso) "¡Dinero recibido!" else "¡Operación exitosa!",
                        color = TextoOscuro, fontWeight = FontWeight.Bold, fontSize = 18.sp
                    )
                    Text(movimiento.tipo.etiqueta, color = TextoGris, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    // Monto en grande, con signo y color (verde entra, rojo sale).
                    Text(
                        (if (esIngreso) "+" else "-") + formatearMonto(movimiento.monto),
                        color = colorMonto, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp
                    )
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = Color(0xFFE5E7EB))
                    Spacer(Modifier.height(8.dp))

                    // Datos del comprobante, en filas "etiqueta: valor".
                    FilaDato("N.º de comprobante", numeroComprobante(movimiento))
                    FilaDato("Fecha y hora", formatearFecha(movimiento.fechaHora))
                    FilaDato("Cuenta", "N.º $cuenta")
                    FilaDato("Titular", titular)
                    FilaDato("Detalle", movimiento.detalle)
                    // La categoría solo aparece en los pagos de servicio ("Servicio Básico").
                    movimiento.categoria?.let { FilaDato("Categoría", it) }

                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = Color(0xFFE5E7EB))
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Comprobante electrónico generado por Billetera CC.\nConsérvalo como respaldo de tu operación.",
                        color = TextoGris, fontSize = 11.sp, textAlign = TextAlign.Center
                    )
                }
            }

            // ---------- BOTONES (fuera del comprobante: no salen en la imagen) ----------
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                // DESCARGAR: guarda la imagen en la Galería (álbum "Billetera CC").
                Button(
                    onClick = {
                        // Android 8 y 9 necesitan el permiso de almacenamiento; desde Android 10 no.
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                            ContextCompat.checkSelfPermission(contexto, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                            PackageManager.PERMISSION_GRANTED
                        ) pedirPermiso.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        else descargar()
                    },
                    enabled = !descargando,
                    modifier = Modifier.weight(1f)
                ) {
                    if (descargando) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    else {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Descargar")
                    }
                }
                // COMPARTIR: convierte el comprobante en imagen y abre el menú para compartir.
                Button(
                    onClick = {
                        compartiendo = true
                        // Se hace en segundo plano porque crear la imagen tarda un instante.
                        alcance.launch {
                            val imagen = capa.toImageBitmap().asAndroidBitmap()
                            compartirImagen(contexto, imagen, "comprobante_${numeroComprobante(movimiento)}")
                            compartiendo = false
                        }
                    },
                    enabled = !compartiendo,
                    modifier = Modifier.weight(1f)
                ) {
                    if (compartiendo) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    else {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Compartir")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onCerrar,
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(containerColor = Color.White)
            ) { Text("Cerrar") }
        }
    }
}

// Una fila del comprobante: etiqueta gris a la izquierda y valor a la derecha.
@Composable
private fun FilaDato(etiqueta: String, valor: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
    ) {
        Text(etiqueta, color = TextoGris, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(valor, color = TextoOscuro, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End,
            modifier = Modifier.weight(1.4f))
    }
}
