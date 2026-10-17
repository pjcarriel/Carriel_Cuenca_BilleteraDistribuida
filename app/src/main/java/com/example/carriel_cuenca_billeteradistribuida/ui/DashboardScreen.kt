// Paquete de la interfaz gráfica.
package com.example.carriel_cuenca_billeteradistribuida.ui

// ---------- Importaciones: piezas de Compose que usaremos ----------
import androidx.compose.foundation.background                 // Pintar el fondo de un elemento.
import androidx.compose.foundation.layout.Arrangement         // Espacio entre elementos.
import androidx.compose.foundation.layout.Box                 // Contenedor simple (lo usamos para el círculo).
import androidx.compose.foundation.layout.Column              // Pone elementos uno DEBAJO del otro.
import androidx.compose.foundation.layout.PaddingValues       // Márgenes internos de una lista.
import androidx.compose.foundation.layout.Row                 // Pone elementos uno AL LADO del otro.
import androidx.compose.foundation.layout.Spacer              // Espacio vacío.
import androidx.compose.foundation.layout.fillMaxWidth        // Ocupar todo el ancho.
import androidx.compose.foundation.layout.height              // Fijar alto.
import androidx.compose.foundation.layout.padding             // Margen interno.
import androidx.compose.foundation.layout.size                // Fijar ancho y alto.
import androidx.compose.foundation.layout.width               // Fijar ancho.
import androidx.compose.foundation.lazy.LazyColumn            // Lista desplazable eficiente.
import androidx.compose.foundation.lazy.items                 // Dibuja un elemento por cada dato de una lista.
import androidx.compose.foundation.shape.CircleShape          // Forma circular.
import androidx.compose.foundation.shape.RoundedCornerShape   // Esquinas redondeadas.
import androidx.compose.material.icons.Icons                  // Catálogo de íconos de Material.
import androidx.compose.material.icons.filled.LocalAtm        // Ícono de billete (retiro).
import androidx.compose.material.icons.filled.NorthEast       // Flecha que sale (transferencia enviada).
import androidx.compose.material.icons.filled.Receipt         // Ícono de factura (pago de servicios).
import androidx.compose.material.icons.filled.Savings         // Ícono de alcancía (depósito).
import androidx.compose.material.icons.filled.SouthWest       // Flecha que entra (transferencia recibida).
import androidx.compose.material.icons.filled.SwapHoriz       // Ícono de intercambio (botón transferir).
import androidx.compose.material3.Card                        // Tarjeta con sombra.
import androidx.compose.material3.CardDefaults                // Valores por defecto de Card (colores, sombra).
import androidx.compose.material3.Icon                        // Dibuja un ícono.
import androidx.compose.material3.MaterialTheme               // Colores y tipografía del tema.
import androidx.compose.material3.Text                        // Muestra texto.
import androidx.compose.runtime.Composable                    // Marca una función que dibuja UI.
import androidx.compose.ui.Alignment                          // Alinear elementos.
import androidx.compose.ui.Modifier                           // Modifica tamaño, margen, color, clics...
import androidx.compose.ui.draw.clip                          // Recorta con una forma (ej. círculo).
import androidx.compose.ui.graphics.Brush                     // Degradado de colores.
import androidx.compose.ui.graphics.Color                     // Tipo Color.
import androidx.compose.ui.graphics.vector.ImageVector        // Tipo de dato de un ícono.
import androidx.compose.ui.text.font.FontWeight               // Grosor de letra (negrita).
import androidx.compose.ui.tooling.preview.Preview            // Vista previa en Android Studio.
import androidx.compose.ui.unit.dp                            // Unidad de tamaño en pantalla.
import androidx.compose.ui.unit.sp                            // Unidad de tamaño de letra.
// Nuestros modelos.
import com.example.carriel_cuenca_billeteradistribuida.model.Movimiento
import com.example.carriel_cuenca_billeteradistribuida.model.TipoOperacion
// Nuestro tema y colores.
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.AzulTarjeta
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.AzulTarjetaClaro
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.Carriel_Cuenca_BilleteraDistribuidaTheme
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.RojoDesconectado
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.RojoEgreso
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.VerdeEnLinea
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.VerdeIngreso
// Herramientas de Java para dar formato a la fecha.
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// =====================================================================
// PANTALLA PRINCIPAL (Dashboard)
// =====================================================================
// "@Composable" = esta función DIBUJA interfaz. Compose la vuelve a ejecutar
// automáticamente cuando cambian sus datos (eso se llama "recomposición").
// Recibe los DATOS por parámetro; no sabe de dónde vienen (hoy: datos falsos;
// después: Room y el servidor). Así la pantalla es fácil de probar y explicar.
@Composable
fun DashboardScreen(
    saldo: Double,                              // Saldo que se muestra en la tarjeta.
    enLinea: Boolean,                           // true = WebSocket conectado.
    movimientos: List<Movimiento>,              // Historial a mostrar.
    // LAMBDA: una función que se pasa como parámetro.
    // "(TipoOperacion) -> Unit" = recibe un TipoOperacion y no devuelve nada.
    // La pantalla solo AVISA qué botón se tocó; quien la usa decide qué hacer.
    onAccionClick: (TipoOperacion) -> Unit,
    modifier: Modifier = Modifier               // Modificador opcional que llega desde afuera.
) {
    // Una sola LazyColumn para TODA la pantalla: así todo se desplaza junto
    // y en pantallas pequeñas no se corta nada.
    LazyColumn(
        modifier = modifier,                                   // Aplicamos el modificador recibido.
        contentPadding = PaddingValues(16.dp),                 // Margen alrededor de todo el contenido.
        verticalArrangement = Arrangement.spacedBy(12.dp)      // 12dp de espacio entre cada elemento.
    ) {
        // "item { }" = agrega UN elemento fijo a la lista.
        item { TarjetaSaldo(saldo = saldo, enLinea = enLinea) }

        // El panel con los 4 botones.
        item { PanelAcciones(onAccionClick = onAccionClick) }

        // Título de la sección de historial.
        item {
            Text(
                text = "Historial",                              // Texto del título.
                style = MaterialTheme.typography.titleLarge,     // Tamaño de título del tema.
                fontWeight = FontWeight.Bold,                    // En negrita.
                modifier = Modifier.padding(top = 8.dp)          // Un poco de espacio arriba.
            )
        }

        // Si no hay movimientos, mostramos un mensaje en lugar de la lista vacía.
        if (movimientos.isEmpty()) {
            item { Text("Aún no tienes movimientos.", color = Color.Gray) }
        }

        // "items(lista)" = dibuja UNA fila por cada movimiento.
        // "key" le dice a Compose cómo identificar cada fila (usa el id).
        // "{ movimiento -> ... }" es una lambda que recibe cada elemento.
        items(movimientos, key = { it.id }) { movimiento ->
            FilaMovimiento(movimiento = movimiento)
        }
    }
}

// =====================================================================
// TARJETA DE SALDO (parte superior)
// =====================================================================
@Composable
fun TarjetaSaldo(saldo: Double, enLinea: Boolean) {
    // Card = tarjeta con esquinas redondeadas y sombra.
    Card(
        modifier = Modifier.fillMaxWidth(),                              // Todo el ancho.
        shape = RoundedCornerShape(24.dp),                               // Esquinas bien redondeadas.
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)  // Sombra.
    ) {
        // Column con fondo DEGRADADO azul (de oscuro a claro, en diagonal).
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(AzulTarjeta, AzulTarjetaClaro)))
                .padding(24.dp)                                          // Margen interno.
        ) {
            // Fila superior: título a la izquierda, indicador de conexión a la derecha.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,        // Separa a los extremos.
                verticalAlignment = Alignment.CenterVertically           // Centrados verticalmente.
            ) {
                Text("Saldo actual", color = Color.White.copy(alpha = 0.8f)) // Blanco algo transparente.
                IndicadorConexion(enLinea = enLinea)
            }
            Spacer(Modifier.height(12.dp))                               // Espacio vertical.
            // El saldo en NÚMEROS GRANDES, como pide el enunciado.
            Text(
                text = formatearMonto(saldo),                            // Ej: "$ 1,250.75".
                color = Color.White,
                fontSize = 40.sp,                                        // Letra grande.
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// =====================================================================
// INDICADOR DEL WEBSOCKET: círculo verde "En línea" o rojo "Desconectado"
// =====================================================================
@Composable
fun IndicadorConexion(enLinea: Boolean) {
    // "if" en Kotlin es una EXPRESIÓN: devuelve un valor que guardamos en una variable.
    val color = if (enLinea) VerdeEnLinea else RojoDesconectado
    val texto = if (enLinea) "En línea" else "Desconectado"

    // Una "píldora" blanca semitransparente que contiene el círculo y el texto.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))                                // Forma de píldora.
            .background(Color.White.copy(alpha = 0.15f))                 // Fondo translúcido.
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        // El círculo: una caja de 10x10 recortada en forma de círculo y pintada.
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(6.dp))                                     // Espacio horizontal.
        Text(texto, color = Color.White, fontSize = 12.sp)
    }
}

// =====================================================================
// PANEL DE ACCIONES: grid de 2 filas x 2 columnas
// =====================================================================
@Composable
fun PanelAcciones(onAccionClick: (TipoOperacion) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Primera fila: Depositar y Retirar.
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // "Modifier.weight(1f)" = cada botón ocupa la MITAD del ancho disponible.
            // "{ onAccionClick(...) }" = lambda que se ejecuta al tocar el botón.
            BotonAccion("Depositar", Icons.Filled.Savings, Modifier.weight(1f)) {
                onAccionClick(TipoOperacion.DEPOSITO)
            }
            BotonAccion("Retirar", Icons.Filled.LocalAtm, Modifier.weight(1f)) {
                onAccionClick(TipoOperacion.RETIRO)
            }
        }
        // Segunda fila: Transferir y Pagar Servicios.
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BotonAccion("Transferir", Icons.Filled.SwapHoriz, Modifier.weight(1f)) {
                onAccionClick(TipoOperacion.TRANSFERENCIA_ENVIADA)
            }
            BotonAccion("Pagar Servicios", Icons.Filled.Receipt, Modifier.weight(1f)) {
                onAccionClick(TipoOperacion.PAGO_SERVICIO)
            }
        }
    }
}

// Un botón grande con ícono y texto (lo reutilizamos 4 veces).
// El último parámetro es una lambda: por eso al llamarlo podemos escribir
// las llaves { } FUERA de los paréntesis (se llama "trailing lambda").
@Composable
fun BotonAccion(
    texto: String,               // Texto debajo del ícono.
    icono: ImageVector,          // Ícono a mostrar.
    modifier: Modifier = Modifier,
    onClick: () -> Unit          // Qué hacer al tocarlo: "() -> Unit" no recibe ni devuelve nada.
) {
    Card(
        onClick = onClick,                                               // La tarjeta entera es tocable.
        modifier = modifier.height(96.dp),                               // Alto fijo: botones grandes.
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer // Color de fondo del tema.
        )
    ) {
        // Ícono arriba y texto abajo, ambos centrados.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icono,
                contentDescription = texto,                              // Texto para lectores de pantalla.
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(texto, fontWeight = FontWeight.SemiBold)
        }
    }
}

// =====================================================================
// FILA DEL HISTORIAL: ícono, tipo, fecha/hora y monto (verde o rojo)
// =====================================================================
@Composable
fun FilaMovimiento(movimiento: Movimiento) {
    // Elegimos color y signo según si el dinero entra o sale.
    val colorMonto = if (movimiento.tipo.esIngreso) VerdeIngreso else RojoEgreso
    val signo = if (movimiento.tipo.esIngreso) "+" else "-"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Ícono dentro de un círculo del color del monto (con transparencia).
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(colorMonto.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center                      // Ícono centrado en el círculo.
            ) {
                Icon(
                    imageVector = iconoDe(movimiento.tipo),
                    contentDescription = movimiento.tipo.etiqueta,
                    tint = colorMonto                                     // Ícono pintado de verde/rojo.
                )
            }
            Spacer(Modifier.width(12.dp))

            // Columna central: tipo, detalle y fecha. "weight(1f)" = toma el espacio sobrante.
            Column(modifier = Modifier.weight(1f)) {
                Text(movimiento.tipo.etiqueta, fontWeight = FontWeight.SemiBold)
                // NULL SAFETY: "?.let { }" solo ejecuta el bloque si categoria NO es null.
                // Así, solo los pagos de servicio muestran la etiqueta "Servicio Básico".
                movimiento.categoria?.let { categoria ->
                    Text(categoria, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                }
                Text(movimiento.detalle, color = Color.Gray, fontSize = 12.sp)
                Text(formatearFecha(movimiento.fechaHora), color = Color.Gray, fontSize = 12.sp)
            }

            // Monto a la derecha, con signo y color. "$signo" inserta la variable en el texto.
            Text(
                text = "$signo${formatearMonto(movimiento.monto)}",
                color = colorMonto,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// =====================================================================
// FUNCIONES DE AYUDA (no dibujan nada, solo calculan)
// =====================================================================

// Devuelve el ícono que corresponde a cada tipo de operación.
// "when" es como un "switch" pero más poderoso. Como TipoOperacion es un enum,
// Kotlin OBLIGA a cubrir todos los casos: si mañana agregas un tipo nuevo
// y olvidas ponerle ícono, el código no compila (¡protección gratis!).
fun iconoDe(tipo: TipoOperacion): ImageVector = when (tipo) {
    TipoOperacion.DEPOSITO -> Icons.Filled.Savings
    TipoOperacion.RETIRO -> Icons.Filled.LocalAtm
    TipoOperacion.TRANSFERENCIA_ENVIADA -> Icons.Filled.NorthEast
    TipoOperacion.TRANSFERENCIA_RECIBIDA -> Icons.Filled.SouthWest
    TipoOperacion.PAGO_SERVICIO -> Icons.Filled.Receipt   // Ícono distinto para "Servicio Básico".
}

// Convierte 1250.75 en "$ 1,250.75" (2 decimales y separador de miles).
fun formatearMonto(monto: Double): String =
    "$ " + String.format(Locale.US, "%,.2f", monto)

// Formato de fecha: día/mes/año hora:minuto. Ej: "17/10/2026 10:30".
private val formatoFecha = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

// Convierte milisegundos (Long) en un texto de fecha legible, en la zona horaria del teléfono.
fun formatearFecha(milisegundos: Long): String =
    Instant.ofEpochMilli(milisegundos)          // Milisegundos -> instante en el tiempo.
        .atZone(ZoneId.systemDefault())         // Instante -> fecha/hora local del teléfono.
        .format(formatoFecha)                   // Fecha/hora -> texto.

// =====================================================================
// VISTA PREVIA: permite ver la pantalla en Android Studio sin ejecutar el emulador
// (pestaña "Split" o "Design" arriba a la derecha del editor).
// =====================================================================
@Preview(showBackground = true, showSystemUi = true)
@Composable
fun DashboardPreview() {
    Carriel_Cuenca_BilleteraDistribuidaTheme {
        DashboardScreen(
            saldo = DatosDePrueba.saldo,
            enLinea = true,
            movimientos = DatosDePrueba.movimientos,
            onAccionClick = { }        // Lambda vacía: en la vista previa no hacemos nada.
        )
    }
}
