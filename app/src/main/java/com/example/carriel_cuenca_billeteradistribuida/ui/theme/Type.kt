// =====================================================================
//   Define cómo se ven las LETRAS de la app: tamaño, grosor y espacio.
// =====================================================================

package com.example.carriel_cuenca_billeteradistribuida.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Estilos de letra de la app. Solo cambiamos el texto normal; el resto queda por defecto.
val Typography = Typography(
    // Texto normal que se lee en casi toda la app.
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,                                       // "sp" respeta si el usuario agrandó la letra.
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)