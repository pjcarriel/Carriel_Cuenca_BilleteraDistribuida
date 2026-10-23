// Paquete de la autenticación.
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

// Mismas reglas que el servidor (validar aquí evita un viaje de red; el servidor valida igual).
// Regex = expresión regular: describe qué forma debe tener un texto.
// Usuario: de 4 a 20 caracteres, solo minúsculas, números o "_" (^ y $ = de principio a fin).
private val PATRON_USUARIO = Regex("^[a-z0-9_]{4,20}$")
// (?=.*[A-Za-z]) = "debe haber al menos una letra"; (?=.*\\d) = "al menos un número".
private val PATRON_PASSWORD = Regex("^(?=.*[A-Za-z])(?=.*\\d).{6,50}$")   // 6+ caracteres, con letra y número.

// PANTALLA DE REGISTRO: crea un usuario con su propia cuenta y $100 de bienvenida.
// La pantalla NO llama al servidor: solo junta los datos y se los pasa a quien la usa (AppRaiz)
// mediante las lambdas "on...". Así la UI queda separada de la lógica (que está en el ViewModel).
@Composable
fun RegistroScreen(
    procesando: Boolean,                          // true = esperando al servidor (se desactivan los botones).
    mensaje: AvisoAcceso?,                        // Respuesta del servidor (error o aviso), o null.
    onRegistrar: (nombre: String, usuario: String, password: String) -> Unit,  // Envía el registro.
    onIrALogin: () -> Unit,                       // Volver a la pantalla de inicio de sesión.
    onEscribir: () -> Unit                        // Al escribir, se borra el mensaje anterior.
) {
    // Un estado por campo de texto. remember = se conserva entre redibujados.
    // "by" = delegado: permite usar "nombre" directamente en vez de "nombre.value".
    var nombre by remember { mutableStateOf("") }
    var usuario by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmar by remember { mutableStateOf("") }
    // Error de validación local (null = todo bien).
    var error by remember { mutableStateOf<String?>(null) }

    // Valida y, si está todo bien, envía. Función local.
    fun enviar() {
        // "when" sin argumento = cadena de if/else: se queda con el PRIMER caso que sea verdadero.
        error = when {
            nombre.trim().length < 3 -> "Escribe tu nombre completo"
            !PATRON_USUARIO.matches(usuario) -> "Usuario: de 4 a 20 letras minúsculas, números o _"
            !PATRON_PASSWORD.matches(password) -> "Contraseña: mínimo 6 caracteres, con letras y números"
            password != confirmar -> "Las contraseñas no coinciden"
            else -> null
        }
        // Solo si no hubo errores se envía al ViewModel (trim() quita espacios al inicio y al final).
        if (error == null) onRegistrar(nombre.trim(), usuario, password)
    }

    // Reutiliza el encabezado azul de LoginScreen; lo que va entre { } son los campos del formulario.
    EncabezadoAcceso(titulo = "Crear cuenta", subtitulo = "Recibirás $ 100.00 de bienvenida") {
        // En cada campo: guardar lo escrito, borrar el error local y avisar que se está escribiendo.
        // ImeAction.Next = la tecla del teclado pasa al SIGUIENTE campo.
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
            // Con contraseñas iguales y no vacías, se marca en verde.
            // isError = true pinta el campo en ROJO (si ya se escribió algo y no coincide).
            isError = confirmar.isNotEmpty() && confirmar != password,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth()
        )
        // Mensaje verde de confirmación cuando ambas contraseñas son iguales.
        if (confirmar.isNotEmpty() && confirmar == password) Text("✔ Las contraseñas coinciden", color = VerdeIngreso, fontSize = 12.sp)

        // Primero el error local; si no hay, el mensaje del servidor (ej. "El usuario ya está registrado").
        // Se copia a "local" porque Kotlin no deja asumir que una variable "var" delegada sigue sin ser null.
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
