// =====================================================================
//   Es el PORTERO de la app: decide si el usuario ve el Login, el Registro
//   o su billetera, y le pregunta "¿Sigues ahí?" si lleva rato sin tocar nada.
// =====================================================================

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.carriel_cuenca_billeteradistribuida.ui.auth.EstadoSesion
import com.example.carriel_cuenca_billeteradistribuida.ui.auth.LoginScreen
import com.example.carriel_cuenca_billeteradistribuida.ui.auth.RegistroScreen
import com.example.carriel_cuenca_billeteradistribuida.ui.auth.SesionViewModel
import com.example.carriel_cuenca_billeteradistribuida.util.PERMISO_RED_LOCAL
import com.example.carriel_cuenca_billeteradistribuida.util.necesitaPermisoRedLocal

// Lo primero que ve el usuario: ruedita → Login/Registro → billetera.
@Composable
fun AppRaiz(sesionViewModel: SesionViewModel = viewModel(factory = SesionViewModel.Factory)) {
    // Datos de la sesión. Cuando cambian, la pantalla se actualiza sola.
    val estado by sesionViewModel.estado.collectAsStateWithLifecycle()                 // Cargando, sin sesión o activa.
    val avisoSegundos by sesionViewModel.avisoSegundos.collectAsStateWithLifecycle()   // Cuenta regresiva, o null.
    val mensaje by sesionViewModel.mensaje.collectAsStateWithLifecycle()               // Aviso para Login/Registro.
    val procesando by sesionViewModel.procesando.collectAsStateWithLifecycle()         // true = esperando al servidor.

    // ---------- PERMISO DE RED LOCAL: se pide antes del login ----------
    val contexto = LocalContext.current
    // true si ya no hace falta pedir el permiso.
    var permisoResuelto by remember { mutableStateOf(!necesitaPermisoRedLocal(contexto)) }
    // Ventanita "¿Permitir...?"; lo de adentro pasa cuando el usuario responde.
    val pedirPermiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        permisoResuelto = true
        if (!concedido) sesionViewModel.avisar("Sin el permiso de red local la app no puede conectarse al servidor")
    }
    // Al abrir la app: si falta el permiso, se pide.
    LaunchedEffect(Unit) { if (!permisoResuelto) pedirPermiso.launch(PERMISO_RED_LOCAL) }

    // ¿Mostrar Login o Registro? (se recuerda aunque se gire el teléfono).
    var mostrarRegistro by rememberSaveable { mutableStateOf(false) }

    // Caja de toda la pantalla que nota cada toque para reiniciar el contador de inactividad.
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        // Espera un toque, avisa actividad y vuelve a esperar (sin bloquear los botones).
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial)
                            sesionViewModel.registrarActividad()
                        }
                    }
                }
        ) {
            // Una pantalla para cada estado de la sesión.
            when (val e = estado) {
                // Cargando: ruedita en el centro.
                EstadoSesion.Cargando ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

                // Sin sesión: Login o Registro.
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

                // Sesión activa: la billetera del usuario (se crea de cero si cambia de usuario).
                is EstadoSesion.Activa ->
                    if (permisoResuelto) key(e.sesion.cuenta) {
                        // Al cerrar sesión, que se vea el Login y no el Registro.
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

    // Aviso "¿Sigues ahí?" con la cuenta regresiva (últimos 30 segundos).
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
