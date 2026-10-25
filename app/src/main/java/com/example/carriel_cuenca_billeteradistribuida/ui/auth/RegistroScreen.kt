// =====================================================================
//   La pantalla "Crear cuenta": el usuario escribe nombre, usuario y
//   contraseña, y recibe una cuenta nueva con $100 de regalo.
// =====================================================================

package com.example.carriel_cuenca_billeteradistribuida.ui.auth

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.VerdeIngreso

// Reglas de formato (las mismas del servidor). Usuario: 4 a 20 minúsculas, números o "_".
private val PATRON_USUARIO = Regex("^[a-z0-9_]{4,20}$")
// Contraseña: al menos 6 caracteres, con al menos una letra y un número.
private val PATRON_PASSWORD = Regex("^(?=.*[A-Za-z])(?=.*\\d).{6,50}$")   // 6+ caracteres, con letra y número.

// PANTALLA DE REGISTRO: junta los datos y se los entrega al SesionViewModel.
@Composable
fun RegistroScreen(
    procesando: Boolean,                          // true = esperando al servidor (se desactivan los botones).
    mensaje: AvisoAcceso?,                        // Respuesta del servidor (error o aviso), o null.
    onRegistrar: (nombre: String, usuario: String, password: String) -> Unit,  // Envía el registro.
    onIrALogin: () -> Unit,                       // Volver a la pantalla de inicio de sesión.
    onEscribir: () -> Unit                        // Al escribir, se borra el mensaje anterior.
) {
    // Lo que el usuario va escribiendo en cada campo (la pantalla lo recuerda).
    var nombre by remember { mutableStateOf("") }
    var usuario by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmar by remember { mutableStateOf("") }
    // Mensaje de error de los datos escritos (null = todo bien).
    var error by remember { mutableStateOf<String?>(null) }

    // Revisa los datos y, si todo está bien, envía el registro.
    fun enviar() {
        // Se revisa en orden y se muestra el primer problema encontrado.
        error = when {
            nombre.trim().length < 3 -> "Escribe tu nombre completo"
            !PATRON_USUARIO.matches(usuario) -> "Usuario: de 4 a 20 letras minúsculas, números o _"
            !PATRON_PASSWORD.matches(password) -> "Contraseña: mínimo 6 caracteres, con letras y números"
            password != confirmar -> "Las contraseñas no coinciden"
            else -> null
        }
        // Solo si no hubo errores se envía (al nombre se le quitan los espacios sobrantes).
        if (error == null) onRegistrar(nombre.trim(), usuario, password)
    }

    // Usa el mismo encabezado azul del Login; debajo van los campos del formulario.
    EncabezadoAcceso(titulo = "Crear cuenta", subtitulo = "Recibirás $ 100.00 de bienvenida") {
        // En cada campo: se guarda lo escrito, se borra el error y se avisa que el usuario está escribiendo.
        // La tecla "Siguiente" del teclado pasa al próximo campo.
        OutlinedTextField(
            value = nombre,
            onValueChange = { nombre = it; error = null; onEscribir() },
            label = { Text("Nombre completo") },
            leadingIcon = { Icon(Icons.Filled.Badge, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )
        // El usuario se guarda sin espacios y en minúsculas mientras se escribe.
        OutlinedTextField(
            value = usuario,
            onValueChange = { usuario = it.trim().lowercase(); error = null; onEscribir() },
            label = { Text("Usuario") },
            supportingText = { Text("4 a 20 caracteres: letras, números o _") },
            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; error = null; onEscribir() },
            label = { Text("Contraseña") },
            supportingText = { Text("Mínimo 6, con letras y números") },
            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
            visualTransformation = PasswordVisualTransformation(),     // Muestra "••••" en vez del texto.
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = confirmar,
            onValueChange = { confirmar = it; error = null; onEscribir() },
            label = { Text("Confirmar contraseña") },
            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            // El campo se pinta de rojo si ya se escribió algo y no coincide con la contraseña.
            isError = confirmar.isNotEmpty() && confirmar != password,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth()
        )
        // Mensaje verde de confirmación cuando ambas contraseñas son iguales.
        if (confirmar.isNotEmpty() && confirmar == password) Text("✔ Las contraseñas coinciden", color = VerdeIngreso, fontSize = 12.sp)

        // Primero se muestra el error de los datos escritos; si no hay, el mensaje del servidor
        // (ej. "El usuario ya está registrado").
        val local = error
        if (local != null) MensajeAcceso(local, esError = true)
        else mensaje?.let { MensajeAcceso(it.texto, it.esError) }

        // Botón principal: mientras se espera al servidor, muestra una ruedita de carga en vez del texto.
        Button(
            onClick = { enviar() },
            enabled = !procesando,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            if (procesando) CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White)
            else Text("Crear cuenta", fontSize = 16.sp)
        }
        TextButton(onClick = onIrALogin, enabled = !procesando) { Text("Ya tengo cuenta: iniciar sesión") }
    }
}
