// =====================================================================
//   Revisa si hay que pedir el permiso de red local, que Android 17
//   exige para que la app pueda hablar con el servidor en el mismo WiFi.
// =====================================================================

package com.example.carriel_cuenca_billeteradistribuida.util

import android.content.Context
import android.content.pm.PackageManager            // Para saber si un permiso está concedido.
import android.os.Build                             // Versión de Android del teléfono.
import androidx.core.content.ContextCompat          // Ayudante para consultar permisos.

// Nombre del permiso de red local (en Android viejos no existe, por eso va como texto).
const val PERMISO_RED_LOCAL = "android.permission.ACCESS_LOCAL_NETWORK"

// Número de versión de Android 17.
private const val API_ANDROID_17 = 37

// true si hay que pedir el permiso: Android 17 o más y el usuario aún no lo dio.
fun necesitaPermisoRedLocal(context: Context): Boolean =
    Build.VERSION.SDK_INT >= API_ANDROID_17 &&
        ContextCompat.checkSelfPermission(context, PERMISO_RED_LOCAL) != PackageManager.PERMISSION_GRANTED
