// =====================================================================
//   Es el "estilo general" de la app: aplica los colores y las letras a
//   todas las pantallas y elige modo claro u oscuro según el teléfono.
// =====================================================================

package com.example.carriel_cuenca_billeteradistribuida.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

// Colores para el MODO OSCURO.
private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

// Colores para el MODO CLARO.
private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

// Tema de la app: envuelve todas las pantallas para que tengan los mismos colores y letras.
@Composable
fun Carriel_Cuenca_BilleteraDistribuidaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),                 // Sigue el modo claro/oscuro del teléfono.
    dynamicColor: Boolean = true,                               // Android 12+: colores según el fondo de pantalla.
    content: @Composable () -> Unit
) {
    // Elegimos los colores: los del fondo de pantalla (Android 12+) o los nuestros.
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // Aplica colores y letras a todo lo que está dentro.
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}