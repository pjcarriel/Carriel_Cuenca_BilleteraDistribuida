// =====================================================================
//   Escribe mensajes en el Logcat con el nombre del hilo que hace cada
//   tarea. Sirve para demostrar qué corre en la pantalla y qué en fondo.
// =====================================================================

package com.example.carriel_cuenca_billeteradistribuida.util

import android.util.Log   // Herramienta de Android para escribir mensajes en el Logcat.

// Etiqueta para filtrar en el Logcat: escribir "tag:HILOS" en el buscador.
const val TAG_HILOS = "HILOS"

// Escribe el mensaje con el nombre del hilo. Ej.: "[main] Click en Depósito".
// "main" = hilo de la pantalla; "DefaultDispatcher-worker-N" = hilo de fondo.
fun logHilo(mensaje: String) {
    Log.d(TAG_HILOS, "[${Thread.currentThread().name}] $mensaje")
}
