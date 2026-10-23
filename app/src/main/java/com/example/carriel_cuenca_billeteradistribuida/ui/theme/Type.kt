// TIPOGRAFÍA DE LA APP: tamaños, grosores y espacios de las letras (los usa Theme.kt).
package com.example.carriel_cuenca_billeteradistribuida.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// TIPOGRAFÍA (Typography de Material 3): conjunto de estilos de texto con NOMBRE
// (bodyLarge, titleLarge, labelSmall...). Text usa bodyLarge si no se le indica otro estilo.
// Solo cambiamos bodyLarge; los demás estilos se quedan con los valores por defecto de Material.
val Typography = Typography(
    // bodyLarge = texto normal del cuerpo (contenido principal).
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,                        // Letra por defecto del sistema.
        fontWeight = FontWeight.Normal,                         // Grosor normal (no negrita).
        fontSize = 16.sp,                                       // "sp" = tamaño que respeta el tamaño de letra del usuario.
        lineHeight = 24.sp,                                     // Alto de cada renglón.
        letterSpacing = 0.5.sp                                  // Espacio extra entre letras.
    )
    // (Plantilla) Otros estilos que se podrían personalizar; están dentro de /* */, así que NO se usan.
    /* Other default text styles to override
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
    */
)