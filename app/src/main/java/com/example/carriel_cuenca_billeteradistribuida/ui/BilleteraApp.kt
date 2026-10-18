// Paquete de la interfaz gráfica.
package com.example.carriel_cuenca_billeteradistribuida.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect                   // Efecto con "limpieza" al salir de pantalla.
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.carriel_cuenca_billeteradistribuida.data.local.SesionEntity
import com.example.carriel_cuenca_billeteradistribuida.model.EstadoOperacion
import com.example.carriel_cuenca_billeteradistribuida.model.TipoOperacion
import com.example.carriel_cuenca_billeteradistribuida.ui.components.OperacionDialog
import com.example.carriel_cuenca_billeteradistribuida.ui.pruebas.PruebasConcurrenciaScreen
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.RojoEgreso
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.VerdeIngreso

// BILLETERA del usuario con sesión activa: une su ViewModel con la pantalla.
@Composable
fun BilleteraApp(
    sesion: SesionEntity,                       // Quién inició sesión (usuario, nombre, cuenta).
    saludo: String?,                            // Mensaje de bienvenida (o null).
    onSaludoMostrado: () -> Unit,
    onCerrarSesion: () -> Unit,
    onActividad: () -> Unit,                    // Reinicia el contador de inactividad.
    onSesionInvalida: (String) -> Unit,         // El servidor rechazó la sesión (401).
    // Un ViewModel POR CUENTA ("key"): si entra otro usuario, tiene su propio estado y repositorio.
    viewModel: BilleteraViewModel = viewModel(
        key = "billetera-${sesion.cuenta}",
        factory = BilleteraViewModel.factory(sesion.cuenta, onSesionInvalida)
    )
) {
    val saldo by viewModel.saldo.collectAsStateWithLifecycle()
    val estadoConexion by viewModel.estadoConexion.collectAsStateWithLifecycle()
    val historial by viewModel.historial.collectAsStateWithLifecycle()
    val estado by viewModel.estado.collectAsStateWithLifecycle()

    var dialogoAbierto by remember { mutableStateOf<TipoOperacion?>(null) }
    var mostrarPruebas by remember { mutableStateOf(false) }      // Panel de pruebas de concurrencia.
    val snackbarHostState = remember { SnackbarHostState() }
    var mensajeEsError by remember { mutableStateOf(false) }

    // Al entrar: sincronizar con el servidor, abrir el WebSocket y mostrar el saludo (una sola vez).
    LaunchedEffect(Unit) {
        viewModel.iniciarConexion()
        saludo?.let { viewModel.mostrarMensaje(it); onSaludoMostrado() }
    }
    // Al SALIR de esta pantalla (cerrar sesión, cambiar de usuario): cerrar el WebSocket.
    DisposableEffect(Unit) {
        onDispose { viewModel.detenerTiempoReal() }
    }

    // Mensajes de resultado (verde = éxito, rojo = error).
    LaunchedEffect(estado) {
        when (val e = estado) {
            is EstadoOperacion.Exito -> {
                mensajeEsError = false
                snackbarHostState.showSnackbar("✔ ${e.mensaje}")
                viewModel.mensajeMostrado()
            }
            is EstadoOperacion.Error -> {
                mensajeEsError = true
                snackbarHostState.showSnackbar("✖ ${e.mensaje}")
                viewModel.mensajeMostrado()
            }
            EstadoOperacion.Inactivo, EstadoOperacion.Procesando -> Unit
        }
    }

    // Si el panel de pruebas está abierto, lo mostramos en lugar del dashboard
    // (el WebSocket y la sesión siguen vivos: no se desmontan).
    if (mostrarPruebas) {
        PruebasConcurrenciaScreen(
            viewModel = viewModel,
            onVolver = { mostrarPruebas = false },
            onActividad = onActividad
        )
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = {
            SnackbarHost(snackbarHostState) { datos ->
                Snackbar(
                    snackbarData = datos,
                    containerColor = if (mensajeEsError) RojoEgreso else VerdeIngreso,
                    contentColor = Color.White
                )
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            val procesando = estado is EstadoOperacion.Procesando
            if (procesando) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            DashboardScreen(
                nombre = sesion.nombre,
                usuario = sesion.usuario,
                cuenta = sesion.cuenta,
                saldo = saldo,
                estadoConexion = estadoConexion,
                movimientos = historial,
                procesando = procesando,
                onAccionClick = { tipo -> dialogoAbierto = tipo },
                onCerrarSesion = onCerrarSesion,
                onAbrirPruebas = { mostrarPruebas = true },
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    dialogoAbierto?.let { tipo ->
        OperacionDialog(
            tipo = tipo,
            saldo = saldo,
            estadoConexion = estadoConexion,
            cuentaPropia = sesion.cuenta,
            // Referencia a la función suspend del ViewModel: el diálogo la llama y espera la factura.
            onConsultarFactura = viewModel::consultarFactura,
            onActividad = onActividad,
            onConfirmar = { solicitud ->
                dialogoAbierto = null
                viewModel.ejecutarOperacion(solicitud)
            },
            onCancelar = { dialogoAbierto = null }
        )
    }
}
