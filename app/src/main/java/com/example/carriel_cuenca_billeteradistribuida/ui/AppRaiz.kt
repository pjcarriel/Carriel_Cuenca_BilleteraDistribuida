// Paquete de la interfaz gráfica.
package com.example.carriel_cuenca_billeteradistribuida.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable         // remember que sobrevive a la rotación.
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput             // Detecta toques en la pantalla.
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.carriel_cuenca_billeteradistribuida.ui.auth.EstadoSesion
import com.example.carriel_cuenca_billeteradistribuida.ui.auth.LoginScreen
import com.example.carriel_cuenca_billeteradistribuida.ui.auth.RegistroScreen
import com.example.carriel_cuenca_billeteradistribuida.ui.auth.SesionViewModel
import com.example.carriel_cuenca_billeteradistribuida.util.PERMISO_RED_LOCAL
import com.example.carriel_cuenca_billeteradistribuida.util.necesitaPermisoRedLocal

// RAÍZ DE LA APP: decide qué pantalla mostrar según el estado de la sesión,
// detecta la ACTIVIDAD del usuario (cada toque) y muestra el aviso de cierre por inactividad.
// viewModel(factory = ...) = obtiene el SesionViewModel; la Factory sabe cómo crearlo con sus dependencias.
// El ViewModel sobrevive a la rotación de pantalla (no se pierde la sesión al girar el teléfono).
@Composable
fun AppRaiz(sesionViewModel: SesionViewModel = viewModel(factory = SesionViewModel.Factory)) {
    // collectAsStateWithLifecycle: cada StateFlow del ViewModel se vuelve estado de Compose;
    // cuando cambia, esta pantalla se redibuja. Deja de escuchar si la app pasa a segundo plano.
    val estado by sesionViewModel.estado.collectAsStateWithLifecycle()
    val avisoSegundos by sesionViewModel.avisoSegundos.collectAsStateWithLifecycle()   // Segundos que faltan, o null.
    val mensaje by sesionViewModel.mensaje.collectAsStateWithLifecycle()               // Aviso para Login/Registro.
    val procesando by sesionViewModel.procesando.collectAsStateWithLifecycle()         // true = esperando al servidor.

    // ---------- PERMISO DE RED LOCAL (Android 17): se pide ANTES del login, que ya usa la red ----------
    val contexto = LocalContext.current
    // permisoResuelto: true si NO hace falta pedirlo (Android antiguo o ya concedido) o si ya respondió.
    var permisoResuelto by remember { mutableStateOf(!necesitaPermisoRedLocal(contexto)) }
    // rememberLauncherForActivityResult: prepara la ventana del sistema "¿Permitir...?".
    // La lambda se ejecuta cuando el usuario responde ("concedido" = true si aceptó).
    val pedirPermiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        permisoResuelto = true
        if (!concedido) sesionViewModel.avisar("Sin el permiso de red local la app no puede conectarse al servidor")
    }
    // LaunchedEffect(Unit) = se ejecuta UNA vez al abrir la app: si falta el permiso, lo pide.
    LaunchedEffect(Unit) { if (!permisoResuelto) pedirPermiso.launch(PERMISO_RED_LOCAL) }

    // ¿Login o Registro? Estado de la pantalla; rememberSaveable lo conserva al rotar.
    var mostrarRegistro by rememberSaveable { mutableStateOf(false) }

    // Caja que ocupa toda la pantalla y DETECTA CADA TOQUE para reiniciar el contador de inactividad.
    // PointerEventPass.Initial: "mira" el toque ANTES que los botones, sin consumirlo (los botones siguen funcionando).
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // pointerInput corre una corrutina que escucha los toques de esta caja.
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        // Bucle infinito: espera (suspende) el siguiente toque, avisa actividad y vuelve a esperar.
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial)
                            sesionViewModel.registrarActividad()    // Reinicia el contador de inactividad.
                        }
                    }
                }
        ) {
            // "when" sobre la sealed interface EstadoSesion: una pantalla por estado.
            // "val e = estado" guarda una copia para poder leer e.sesion dentro de cada caso.
            when (val e = estado) {
                // Cargando: se está leyendo de Room si había una sesión guardada. Ruedita centrada.
                EstadoSesion.Cargando ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

                // Sin sesión: Login o Registro según "mostrarRegistro".
                // "sesionViewModel::registrar" = referencia a la función (se pasa sin llamarla).
                // mensajeVisto() borra el aviso anterior al cambiar de pantalla o al escribir.
                EstadoSesion.SinSesion ->
                    if (mostrarRegistro) {
                        RegistroScreen(
                            procesando = procesando,
                            mensaje = mensaje,
                            onRegistrar = sesionViewModel::registrar,
                            onIrALogin = { mostrarRegistro = false; sesionViewModel.mensajeVisto() },
                            onEscribir = sesionViewModel::mensajeVisto
                        )
                    } else {
                        LoginScreen(
                            procesando = procesando,
                            mensaje = mensaje,
                            onIngresar = sesionViewModel::login,
                            onIrARegistro = { mostrarRegistro = true; sesionViewModel.mensajeVisto() },
                            onEscribir = sesionViewModel::mensajeVisto
                        )
                    }

                // Sesión activa: se muestra la billetera del usuario. "is" = comprueba el tipo
                // (Activa es una clase con datos: la sesión y el saludo de bienvenida).
                is EstadoSesion.Activa ->
                    // key(cuenta): si cambia el usuario, Compose crea TODO de nuevo (otro ViewModel, otro WebSocket).
                    // Solo conectamos cuando el permiso de red ya se resolvió.
                    if (permisoResuelto) key(e.sesion.cuenta) {
                        // Al entrar, la próxima vez que se cierre sesión se verá el Login (no el Registro).
                        // LaunchedEffect: cambiar estado DENTRO del dibujado no está permitido; aquí sí.
                        LaunchedEffect(Unit) { mostrarRegistro = false }
                        BilleteraApp(
                            sesion = e.sesion,
                            saludo = e.saludo,
                            onSaludoMostrado = sesionViewModel::saludoMostrado,
                            onCerrarSesion = sesionViewModel::cerrarSesion,
                            onActividad = sesionViewModel::registrarActividad,
                            onSesionInvalida = sesionViewModel::sesionInvalida
                        )
                    }
            }
        }
    }

    // ---------- AVISO DE CIERRE POR INACTIVIDAD (últimos 30 segundos) ----------
    // Solo se dibuja si avisoSegundos NO es null ("?.let"). Como avisoSegundos cambia cada segundo,
    // el diálogo se redibuja solo y la cuenta regresiva baja en pantalla.
    // Está FUERA del Surface para quedar encima de cualquier pantalla.
    avisoSegundos?.let { segundos ->
        AlertDialog(
            onDismissRequest = sesionViewModel::registrarActividad,     // Tocar fuera = seguir conectado.
            icon = { Icon(Icons.Filled.Timer, contentDescription = null) },
            title = { Text("¿Sigues ahí?") },
            text = { Text("Por seguridad, tu sesión se cerrará en $segundos segundos por inactividad.") },
            confirmButton = { Button(onClick = sesionViewModel::registrarActividad) { Text("Seguir conectado") } },
            dismissButton = { TextButton(onClick = sesionViewModel::cerrarSesion) { Text("Cerrar sesión") } }
        )
    }
}
