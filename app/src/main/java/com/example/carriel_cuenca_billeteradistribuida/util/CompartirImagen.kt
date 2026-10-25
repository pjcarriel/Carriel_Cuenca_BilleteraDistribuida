// =====================================================================
//   Los botones Compartir y Descargar del comprobante: enviar la imagen
//   por otras apps o guardarla en la Galería.
// =====================================================================

package com.example.carriel_cuenca_billeteradistribuida.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaScannerConnection     // Avisa a la Galería que hay una imagen nueva (Android 8 y 9).
import android.os.Build
import android.os.Environment
import android.provider.MediaStore              // Donde Android guarda las fotos de la Galería.
import androidx.core.content.FileProvider       // Pasa un archivo nuestro a otra app de forma segura.
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// Carpeta privada donde se guarda la imagen antes de compartirla (igual que en rutas_compartidas.xml).
private const val CARPETA = "comprobantes"

// Nombre del álbum en la Galería.
const val ALBUM_COMPROBANTES = "Billetera CC"

// Botón COMPARTIR: guarda la imagen y abre la lista de apps (WhatsApp, correo...).
// El guardado se hace en un hilo de fondo para no congelar la pantalla.
suspend fun compartirImagen(contexto: Context, imagen: Bitmap, nombre: String) {
    val archivo = withContext(Dispatchers.IO) {
        logHilo("Compartir: guardando $nombre.png")
        val carpeta = File(contexto.cacheDir, CARPETA).apply { mkdirs() }
        File(carpeta, "$nombre.png").also { f ->
            f.outputStream().use { salida -> imagen.compress(Bitmap.CompressFormat.PNG, 100, salida) }
        }
    }
    // Enlace temporal para que solo la app elegida pueda leer la imagen.
    val uri = FileProvider.getUriForFile(contexto, "${contexto.packageName}.archivos", archivo)
    val intento = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, "Comprobante de Billetera CC")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    // Muestra la lista de apps para compartir.
    contexto.startActivity(Intent.createChooser(intento, "Compartir comprobante").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

// Botón DESCARGAR: guarda la imagen en la Galería. Devuelve true si se guardó.
// Se hace en un hilo de fondo porque escribe en la memoria del teléfono.
suspend fun guardarEnGaleria(contexto: Context, imagen: Bitmap, nombre: String): Boolean = withContext(Dispatchers.IO) {
    logHilo("Descargar: guardando $nombre.png en la Galería")
    runCatching {
        // Android 10 o más: no necesita permiso.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = contexto.contentResolver
            val datos = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$nombre.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM_COMPROBANTES")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            // Crea el espacio para la imagen (si falla, devuelve false).
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, datos) ?: return@runCatching false
            resolver.openOutputStream(uri)?.use { salida -> imagen.compress(Bitmap.CompressFormat.PNG, 100, salida) }
            datos.clear()
            datos.put(MediaStore.Images.Media.IS_PENDING, 0)    // Lista: ya se ve en la Galería.
            resolver.update(uri, datos, null, null)
            true
        } else {
            // Android 8 y 9: se guarda en la carpeta Imágenes y se avisa a la Galería.
            @Suppress("DEPRECATION")                              // Única forma en Android 8 y 9.
            val carpeta = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), ALBUM_COMPROBANTES)
            carpeta.mkdirs()
            val archivo = File(carpeta, "$nombre.png")
            archivo.outputStream().use { salida -> imagen.compress(Bitmap.CompressFormat.PNG, 100, salida) }
            MediaScannerConnection.scanFile(contexto, arrayOf(archivo.absolutePath), arrayOf("image/png"), null)
            true
        }
    }.getOrElse { e ->
        // Si falla, se anota en el Logcat y devuelve false (la app no se cierra).
        logHilo("Descargar: no se pudo guardar (${e.javaClass.simpleName}: ${e.message})")
        false
    }
}
