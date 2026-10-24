// Paquete de utilidades generales.
package com.example.carriel_cuenca_billeteradistribuida.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider       // Entrega archivos privados a otras apps de forma segura.
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// Carpeta (dentro de la caché privada de la app) donde se guardan las imágenes a compartir.
// Debe coincidir con res/xml/rutas_compartidas.xml.
private const val CARPETA = "comprobantes"

// Guarda la imagen como PNG y abre el menú "Compartir" de Android (WhatsApp, correo, Drive...).
//  1. Guardar el archivo es DISCO: se hace en Dispatchers.IO (nunca en el hilo main).
//  2. Otra app no puede leer los archivos privados de la nuestra: FileProvider crea una dirección
//     "content://" temporal y FLAG_GRANT_READ_URI_PERMISSION le da permiso SOLO a la app elegida.
suspend fun compartirImagen(contexto: Context, imagen: Bitmap, nombre: String) {
    val archivo = withContext(Dispatchers.IO) {
        logHilo("Compartir: guardando $nombre.png")
        val carpeta = File(contexto.cacheDir, CARPETA).apply { mkdirs() }
        File(carpeta, "$nombre.png").also { f ->
            f.outputStream().use { salida -> imagen.compress(Bitmap.CompressFormat.PNG, 100, salida) }
        }
    }
    // De vuelta en el hilo main: abrir otra pantalla (el menú de compartir) es trabajo de la UI.
    val uri = FileProvider.getUriForFile(contexto, "${contexto.packageName}.archivos", archivo)
    val intento = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, "Comprobante de Billetera CC")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    // createChooser: muestra la lista de apps; FLAG_ACTIVITY_NEW_TASK por si el contexto no es una Activity.
    contexto.startActivity(Intent.createChooser(intento, "Compartir comprobante").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
