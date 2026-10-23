// Paquete de utilidades generales.
package com.example.carriel_cuenca_billeteradistribuida.util

import android.util.Log   // Herramienta de Android para escribir mensajes en el Logcat.

// Etiqueta (tag) con la que filtraremos en el Logcat durante la defensa.
// En Android Studio: pestaña Logcat -> escribir "tag:HILOS" en el buscador.
const val TAG_HILOS = "HILOS"

// Escribe un mensaje en el Logcat indicando EN QUÉ HILO se está ejecutando.
// Thread.currentThread().name devuelve el nombre del hilo actual:
//   - "main"                      -> hilo principal (UI, Dispatchers.Main).
//   - "DefaultDispatcher-worker-N" -> hilo de fondo (Dispatchers.IO).
// Ejemplo de salida:  [main] Click en Depósito
// Log.d = mensaje de nivel DEBUG (depuración). Función de nivel superior: se usa desde cualquier archivo.
fun logHilo(mensaje: String) {
    Log.d(TAG_HILOS, "[${Thread.currentThread().name}] $mensaje")
}
