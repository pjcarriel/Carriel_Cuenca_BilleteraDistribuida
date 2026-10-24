// Paquete de utilidades generales.
package com.example.carriel_cuenca_billeteradistribuida.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaScannerConnection     // Avisa a la Galería que hay una imagen nueva (Android 8 y 9).
import android.os.Build
import android.os.Environment
import android.provider.MediaStore              // "Base de datos" de fotos y archivos compartidos de Android.
import androidx.core.content.FileProvider       // Entrega archivos privados a otras apps de forma segura.
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// Carpeta (dentro de la caché privada de la app) donde se guardan las imágenes a compartir.
// Debe coincidir con res/xml/rutas_compartidas.xml.
private const val CARPETA = "comprobantes"

// Álbum de la Galería donde se DESCARGAN los comprobantes (Imágenes/Billetera CC).
const val ALBUM_COMPROBANTES = "Billetera CC"

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

// DESCARGA la imagen en la GALERÍA del teléfono (álbum "Billetera CC"). Devuelve true si se guardó.
// Todo es disco: corre en Dispatchers.IO.
//  - Android 10 o superior: MediaStore. No hace falta ningún permiso; IS_PENDING = 1 oculta la imagen
//    mientras se escribe y luego se pone en 0 para que aparezca en la Galería ya completa.
//  - Android 8 y 9: se escribe en la carpeta pública Imágenes (requiere el permiso WRITE_EXTERNAL_STORAGE,
//    que el diálogo pide antes de llamar aquí) y se avisa a la Galería con MediaScannerConnection.
suspend fun guardarEnGaleria(contexto: Context, imagen: Bitmap, nombre: String): Boolean = withContext(Dispatchers.IO) {
    logHilo("Descargar: guardando $nombre.png en la Galería")
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = contexto.contentResolver
            val datos = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$nombre.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM_COMPROBANTES")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            // insert crea la entrada en la Galería y devuelve su dirección (o null si falla).
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, datos) ?: return@runCatching false
            resolver.openOutputStream(uri)?.use { salida -> imagen.compress(Bitmap.CompressFormat.PNG, 100, salida) }
            datos.clear()
            datos.put(MediaStore.Images.Media.IS_PENDING, 0)    // Lista: ya se puede ver en la Galería.
            resolver.update(uri, datos, null, null)
            true
        } else {
            @Suppress("DEPRECATION")                              // Única forma en Android 8 y 9.
            val carpeta = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), ALBUM_COMPROBANTES)
            carpeta.mkdirs()
            val archivo = File(carpeta, "$nombre.png")
            archivo.outputStream().use { salida -> imagen.compress(Bitmap.CompressFormat.PNG, 100, salida) }
            MediaScannerConnection.scanFile(contexto, arrayOf(archivo.absolutePath), arrayOf("image/png"), null)
            true
        }
    }.getOrElse { e ->
        logHilo("Descargar: no se pudo guardar (${e.javaClass.simpleName}: ${e.message})")
        false
    }
}
