// Paquete de utilidades generales.
package com.example.carriel_cuenca_billeteradistribuida.util

import android.content.Context
import android.content.pm.PackageManager            // Para consultar si un permiso está concedido.
import android.os.Build                             // Versión de Android del teléfono.
import androidx.core.content.ContextCompat          // Ayudante para consultar permisos.

// Nombre del permiso de red local de Android 17. Lo escribimos como texto
// porque en versiones anteriores de Android no existe.
const val PERMISO_RED_LOCAL = "android.permission.ACCESS_LOCAL_NETWORK"

// Versión de Android desde la que se exige el permiso (Android 17 = API 37).
private const val API_ANDROID_17 = 37

// Devuelve true si HAY QUE PEDIR el permiso: el teléfono es Android 17 o superior
// y el usuario todavía no lo ha concedido. En Android 16 o menor devuelve false
// (allí la red local no está restringida y el permiso ni siquiera existe).
// "&&" evalúa en CORTOCIRCUITO: si el Android es menor a 17, ni siquiera consulta el permiso.
fun necesitaPermisoRedLocal(context: Context): Boolean =
    Build.VERSION.SDK_INT >= API_ANDROID_17 &&
        ContextCompat.checkSelfPermission(context, PERMISO_RED_LOCAL) != PackageManager.PERMISSION_GRANTED
