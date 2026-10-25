// =====================================================================
//   La pantalla de inicio de sesión: el usuario escribe su usuario y
//   contraseña para entrar. También permite cambiar la dirección del servidor.
// =====================================================================

package com.example.carriel_cuenca_billeteradistribuida.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding           // Deja espacio para el teclado.
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll             // Permite desplazar en pantallas pequeñas.
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.carriel_cuenca_billeteradistribuida.data.remote.ConfigServidor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation   // Muestra "••••" en vez de la contraseña.
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.AzulTarjeta
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.AzulTarjetaClaro

// PANTALLA DE INICIO DE SESIÓN: entrega usuario y contraseña al SesionViewModel.
@Composable
fun LoginScreen(
    procesando: Boolean,                         // true = esperando al servidor.
    mensaje: AvisoAcceso?,                       // Error o aviso (ej. "sesión cerrada por inactividad").
    onIngresar: (usuario: String, password: String) -> Unit,   // Inicia sesión con los datos escritos.
    onIrARegistro: () -> Unit,                   // Cambia a la pantalla de registro.
    onEscribir: () -> Unit                       // Al escribir, se borra el mensaje anterior.
) {
    // Lo que el usuario va escribiendo (la pantalla lo recuerda).
    var usuario by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var verPassword by remember { mutableStateOf(false) }      // Ojo para mostrar/ocultar la contraseña.

    // El botón solo se habilita si hay datos y no se está esperando al servidor.
    val puedeIngresar = usuario.isNotBlank() && password.isNotBlank() && !procesando

    // Encabezado azul; debajo va el formulario.
    EncabezadoAcceso(titulo = "Billetera Distribuida", subtitulo = "Inicia sesión para continuar") {
        // Campo de usuario: se guarda sin espacios y en minúsculas.
        OutlinedTextField(
            value = usuario,
            onValueChange = { usuario = it.trim().lowercase(); onEscribir() },
            label = { Text("Usuario") },
            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )
        // Campo de contraseña (el teclado no muestra sugerencias).
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; onEscribir() },
            label = { Text("Contraseña") },
            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
            // Botón "ojo": alterna entre ver y ocultar la contraseña.
            trailingIcon = {
                IconButton(onClick = { verPassword = !verPassword }) {
                    Icon(if (verPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (verPassword) "Ocultar contraseña" else "Mostrar contraseña")
                }
            },
            // Se ve el texto o se ven puntos "••••", según el botón del ojo.
            visualTransformation = if (verPassword) VisualTransformation.None else PasswordVisualTransformation(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth()
        )

        // Mensaje de error o aviso (solo si hay alguno).
        mensaje?.let { MensajeAcceso(it.texto, it.esError) }

        // Botón "Ingresar": mientras se espera al servidor, muestra una ruedita de carga en vez del texto.
        Button(
            onClick = { onIngresar(usuario, password) },
            enabled = puedeIngresar,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            if (procesando) CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White)
            else Text("Ingresar", fontSize = 16.sp)
        }
        TextButton(onClick = onIrARegistro, enabled = !procesando) { Text("¿No tienes cuenta? Crear cuenta") }
        Text("Demo: pamela / demo123", color = Color.Gray, fontSize = 12.sp)

        // Configurar la dirección del servidor (necesario en un teléfono real).
        var mostrarConfig by remember { mutableStateOf(false) }
        // Dirección que se muestra; al guardar una nueva, el texto se actualiza al instante.
        var hostUi by remember { mutableStateOf(ConfigServidor.host()) }
        // Botón pequeño que muestra el servidor actual; al tocarlo abre la ventana de configuración.
        TextButton(onClick = { mostrarConfig = true }) {
            Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Servidor: $hostUi", fontSize = 12.sp)
        }
        // La ventana solo aparece mientras mostrarConfig sea true.
        if (mostrarConfig) ConfigServidorDialog(
            onGuardado = { hostUi = it },
            onCerrar = { mostrarConfig = false }
        )
    }
}

// Ventana para cambiar la dirección del servidor:
//   emulador = 10.0.2.2; misma wifi = IP de la PC; cualquier red = dirección https del túnel de Cloudflare.
@Composable
private fun ConfigServidorDialog(onGuardado: (String) -> Unit, onCerrar: () -> Unit) {
    // Acceso a Android, necesario para guardar la dirección en el teléfono.
    val contexto = LocalContext.current
    // Texto editable del campo; empieza con la dirección guardada actualmente.
    var host by remember { mutableStateOf(ConfigServidor.host()) }
    // Error si la dirección escrita no es válida (null = sin error).
    var error by remember { mutableStateOf<String?>(null) }
    // Ventana emergente; tocar fuera o "atrás" la cierra.
    AlertDialog(
        onDismissRequest = onCerrar,
        icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
        title = { Text("Dirección del servidor") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it.trim(); error = null },
                    label = { Text("IP o dirección del servidor") },
                    singleLine = true,
                    isError = error != null,                       // Borde rojo si hay error.
                    // Mensaje de error debajo del campo (solo si hay error).
                    supportingText = error?.let { { Text(it) } },
                    // Teclado de direcciones web (muestra "/", ":" y ".com").
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
                Text("• Emulador: 10.0.2.2", fontSize = 12.sp, color = Color.Gray)
                Text("• Misma wifi: la IP de la PC (ej. 192.168.1.199); se usa el puerto ${ConfigServidor.PUERTO}", fontSize = 12.sp, color = Color.Gray)
                Text("• Desde cualquier red: la dirección del túnel (ej. https://algo.trycloudflare.com)", fontSize = 12.sp, color = Color.Gray)
            }
        },
        confirmButton = {
            // Guardar: revisa la dirección y, si es válida, la guarda, la muestra en el Login y cierra.
            // Si no es válida, muestra el error.
            Button(onClick = {
                if (ConfigServidor.guardarHost(contexto, host)) {
                    onGuardado(ConfigServidor.host())
                    onCerrar()
                } else {
                    error = "Dirección no válida. Ej. 192.168.1.199 o https://algo.trycloudflare.com"
                }
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } }
    )
}

// Diseño común de Login y Registro: encabezado azul con ícono y, debajo, el formulario.
// "contenido" son los campos que manda cada pantalla.
@Composable
fun EncabezadoAcceso(titulo: String, subtitulo: String, contenido: @Composable () -> Unit) {
    // Toda la pantalla: se puede desplazar y deja espacio para el teclado.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
    ) {
        // Encabezado con degradado (mismo estilo que la tarjeta de saldo).
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(AzulTarjeta, AzulTarjetaClaro)))
                .padding(top = 72.dp, bottom = 40.dp)
        ) {
            Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, tint = Color.White, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(12.dp))
            Text(titulo, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(subtitulo, color = Color.White.copy(alpha = 0.8f))
        }
        // Formulario.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            contenido()                     // Aquí se dibujan los campos que pasó cada pantalla.
        }
    }
}

// Caja de mensaje: ROJA si es un error, AZUL si es solo información (ej. "Cerraste sesión").
@Composable
fun MensajeAcceso(texto: String, esError: Boolean) {
    // Color de fondo y color del texto según si es error o información.
    val fondo = if (esError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
    val color = if (esError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
    Text(
        text = texto,
        color = color,
        fontSize = 14.sp,
        // Caja con esquinas redondeadas, color de fondo y margen interno.
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(fondo)
            .padding(12.dp)
    )
}
