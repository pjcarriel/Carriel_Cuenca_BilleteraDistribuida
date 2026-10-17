// Paquete de la interfaz gráfica.
package com.example.carriel_cuenca_billeteradistribuida.ui

import androidx.activity.compose.rememberLauncherForActivityResult     // Abre diálogos del sistema y recibe la respuesta.
import androidx.activity.result.contract.ActivityResultContracts       // Contrato "pedir un permiso".
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.platform.LocalContext                       // Contexto de Android dentro de Compose.
import com.example.carriel_cuenca_billeteradistribuida.util.PERMISO_RED_LOCAL
import com.example.carriel_cuenca_billeteradistribuida.util.necesitaPermisoRedLocal
import androidx.compose.foundation.layout.fillMaxSize                 // Ocupar toda la pantalla.
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding                     // Margen interno.
import androidx.compose.material3.LinearProgressIndicator             // Barra de progreso animada.
import androidx.compose.material3.Scaffold                            // Estructura base de la pantalla.
import androidx.compose.material3.SnackbarHost                        // Lugar donde aparecen los Snackbars.
import androidx.compose.material3.SnackbarHostState                   // Controla qué Snackbar se muestra.
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect                        // Lanza una corrutina desde Compose.
import androidx.compose.runtime.getValue                              // Permite usar "by".
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember                              // Recuerda un valor entre redibujados.
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle         // StateFlow -> estado de Compose.
import androidx.lifecycle.viewmodel.compose.viewModel                 // Obtiene (o crea) el ViewModel.
import com.example.carriel_cuenca_billeteradistribuida.model.EstadoOperacion
import com.example.carriel_cuenca_billeteradistribuida.model.TipoOperacion

// Pantalla "raíz" de la app: une el ViewModel con la UI.
@Composable
fun BilleteraApp(viewModel: BilleteraViewModel = viewModel(factory = BilleteraViewModel.Factory)) {

    // Leemos cada StateFlow del ViewModel como estado de Compose.
    val saldo by viewModel.saldo.collectAsStateWithLifecycle()
    val estadoConexion by viewModel.estadoConexion.collectAsStateWithLifecycle()
    val historial by viewModel.historial.collectAsStateWithLifecycle()
    val estado by viewModel.estado.collectAsStateWithLifecycle()

    // Qué diálogo está abierto: null = ninguno. Es estado SOLO de la pantalla (no del ViewModel).
    var dialogoAbierto by remember { mutableStateOf<TipoOperacion?>(null) }

    // Estado del Snackbar (mensaje temporal en la parte inferior).
    val snackbarHostState = remember { SnackbarHostState() }

    // ---------- PERMISO DE RED LOCAL (Android 17) ----------
    val contexto = LocalContext.current
    // Lanzador del diálogo del SISTEMA que pide un permiso. La lambda recibe la respuesta del usuario.
    val pedirPermiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        if (concedido) viewModel.iniciarConexion()       // Ya podemos hablar con el servidor (REST + WebSocket).
        else viewModel.permisoRedLocalDenegado()        // Sin permiso: solo datos locales.
    }
    // LaunchedEffect(Unit): se ejecuta UNA vez al mostrar la pantalla (y al recrearla, ej. al rotar).
    LaunchedEffect(Unit) {
        if (necesitaPermisoRedLocal(contexto)) pedirPermiso.launch(PERMISO_RED_LOCAL)  // Muestra el diálogo.
        else viewModel.iniciarConexion()                                               // Ya concedido: conectar.
    }

    // Cada vez que "estado" cambia, mostramos el mensaje correspondiente.
    LaunchedEffect(estado) {
        when (val e = estado) {
            is EstadoOperacion.Exito -> {
                snackbarHostState.showSnackbar("✔ ${e.mensaje}")
                viewModel.mensajeMostrado()
            }
            is EstadoOperacion.Error -> {
                snackbarHostState.showSnackbar("✖ ${e.mensaje}")   // Ej.: "✖ Saldo insuficiente: ..."
                viewModel.mensajeMostrado()
            }
            EstadoOperacion.Inactivo, EstadoOperacion.Procesando -> Unit
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            // Mientras una operación está en curso, mostramos una barra de progreso arriba.
            if (estado is EstadoOperacion.Procesando) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            DashboardScreen(
                saldo = saldo,
                estadoConexion = estadoConexion,
                movimientos = historial,
                // Al tocar un botón, solo ABRIMOS el diálogo de ese tipo.
                onAccionClick = { tipo -> dialogoAbierto = tipo },
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    // Si hay un diálogo abierto, lo dibujamos encima de todo.
    // "?.let { tipo -> }" = solo si dialogoAbierto no es null; "tipo" es su valor ya sin "?".
    dialogoAbierto?.let { tipo ->
        OperacionDialog(
            tipo = tipo,
            onConfirmar = { solicitud ->
                dialogoAbierto = null                       // Cerramos el diálogo...
                viewModel.ejecutarOperacion(solicitud)      // ...y enviamos la operación al servidor.
            },
            onCancelar = { dialogoAbierto = null }
        )
    }
}
