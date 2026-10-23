// TEMA DE LA APP (Material 3): define los COLORES y las LETRAS que usan todas las pantallas.
package com.example.carriel_cuenca_billeteradistribuida.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

// ESQUEMA DE COLORES OSCURO (Material 3 / colorScheme).
// Un colorScheme es un conjunto de colores con NOMBRE (primary, secondary, tertiary...).
// Los componentes (Button, Card, Text...) toman sus colores de aquí en vez de tenerlos fijos.
// Se usa en MODO OSCURO cuando no hay color dinámico. "private" = solo se usa en este archivo.
private val DarkColorScheme = darkColorScheme(
    primary = Purple80,                                         // Color principal (botones, textos destacados).
    secondary = PurpleGrey80,                                   // Color secundario (acentos menos importantes).
    tertiary = Pink80                                           // Tercer color de acento.
)

// ESQUEMA DE COLORES CLARO: los mismos nombres, con tonos pensados para fondo blanco (modo claro).
private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40

    // (Plantilla) Otros colores que se podrían cambiar; están dentro de /* */, así que NO se usan.
    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

// FUNCIÓN DEL TEMA: un @Composable que ENVUELVE a toda la interfaz (se usa en MainActivity
// y en la vista previa del Dashboard). Todo lo que se dibuja dentro de "content" puede leer
// los colores con MaterialTheme.colorScheme y las letras con MaterialTheme.typography.
@Composable
fun Carriel_Cuenca_BilleteraDistribuidaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),                 // Por defecto sigue el modo claro/oscuro del teléfono.
    // COLOR DINÁMICO (dynamicColor): en Android 12+ (API 31, "S") los colores se generan
    // a partir del FONDO DE PANTALLA del usuario. true = activado.
    dynamicColor: Boolean = true,
    // Contenido a dibujar con este tema: una lambda @Composable (lo que va entre las llaves { }).
    content: @Composable () -> Unit
) {
    // Elegimos el esquema de colores. "when" sin argumento: se usa el PRIMER caso que sea verdadero.
    val colorScheme = when {
        // 1) Color dinámico activado Y el teléfono tiene Android 12 o superior: colores del sistema.
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current                  // Context de Android (lo piden las funciones dynamic...).
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        // 2) Si no hay color dinámico (Android más viejo): nuestros esquemas fijos de arriba.
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // MaterialTheme aplica el esquema de colores y la tipografía a todo el contenido.
    MaterialTheme(
        colorScheme = colorScheme,                              // Colores elegidos arriba.
        typography = Typography,                                // Estilos de texto definidos en Type.kt.
        content = content                                       // Lo que se dibuja dentro del tema.
    )
}