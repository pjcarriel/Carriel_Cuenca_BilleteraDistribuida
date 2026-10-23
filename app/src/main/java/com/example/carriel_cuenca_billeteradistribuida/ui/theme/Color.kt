// COLORES DE LA APP: constantes que usan el tema (Theme.kt) y los componentes.
package com.example.carriel_cuenca_billeteradistribuida.ui.theme

import androidx.compose.ui.graphics.Color                       // Clase Color de Compose.

// Colores que trae la plantilla (los usa Theme.kt).
// "80" = tonos CLAROS para el modo oscuro; "40" = tonos OSCUROS para el modo claro.
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// ---------- Colores de nuestra billetera (estilo fintech) ----------
// Formato 0xAARRGGBB: AA = opacidad (FF = sólido), luego rojo, verde y azul.

// Azul oscuro de la tarjeta de saldo.
val AzulTarjeta = Color(0xFF1E3A8A)
// Azul un poco más claro para el degradado de la tarjeta.
val AzulTarjetaClaro = Color(0xFF3B82F6)
// Verde para montos que ENTRAN (depósito, transferencia recibida).
val VerdeIngreso = Color(0xFF2E7D32)
// Rojo para montos que SALEN (retiro, transferencia enviada, pago de servicio).
val RojoEgreso = Color(0xFFC62828)
// Verde brillante del círculo "En línea".
val VerdeEnLinea = Color(0xFF22C55E)
// Ámbar del círculo "Conectando..." (intentando abrir o reabrir el WebSocket).
val AmbarConectando = Color(0xFFF59E0B)
// Rojo del círculo "Desconectado".
val RojoDesconectado = Color(0xFFEF4444)
