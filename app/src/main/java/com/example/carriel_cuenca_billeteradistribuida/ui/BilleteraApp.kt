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
// Dibuja el Dashboard (o el panel de pruebas), los avisos (Snackbar) y el diálogo de operación.
@Composable
fun BilleteraApp(
    sesion: SesionEntity,                       // Quién inició sesión (usuario, nombre, cuenta).
    saludo: String?,                            // Mensaje de bienvenida (o null).
    onSaludoMostrado: () -> Unit,               // Avisa que el saludo ya se mostró (para no repetirlo).
    onCerrarSesion: () -> Unit,                 // Botón de cerrar sesión.
    onActividad: () -> Unit,                    // Reinicia el contador de inactividad.
    onSesionInvalida: (String) -> Unit,         // El servidor rechazó la sesión (401).
    // Un ViewModel POR CUENTA ("key"): si entra otro usuario, tiene su propio estado y repositorio.
    viewModel: BilleteraViewModel = viewModel(
        key = "billetera-${sesion.cuenta}",
        factory = BilleteraViewModel.factory(sesion.cuenta, onSesionInvalida)
    )
) {
    // Estado que viene del ViewModel. collectAsStateWithLifecycle = escucha cada StateFlow y
    // redibuja cuando cambia (ej. llega una transferencia por WebSocket y sube el saldo).
    val saldo by viewModel.saldo.collectAsStateWithLifecycle()
    val estadoConexion by viewModel.estadoConexion.collectAsStateWithLifecycle()   // EN_LINEA, CONECTANDO...
    val historial by viewModel.historial.collectAsStateWithLifecycle()             // Movimientos (desde Room).
    val estado by viewModel.estado.collectAsStateWithLifecycle()                   // Resultado de la última operación.
    val nodosConectados by viewModel.nodosConectados.collectAsStateWithLifecycle()

    // Estado propio de la pantalla (remember = se conserva entre redibujados).
    // dialogoAbierto: qué operación se está haciendo (depósito, retiro...) o null = ningún diálogo.
    var dialogoAbierto by remember { mutableStateOf<TipoOperacion?>(null) }
    var mostrarPruebas by remember { mutableStateOf(false) }      // Panel de pruebas de concurrencia.
    // SnackbarHostState = controla la barrita de mensajes que aparece abajo.
    val snackbarHostState = remember { SnackbarHostState() }
    var mensajeEsError by remember { mutableStateOf(false) }      // Decide el color del Snackbar.

    // Al entrar: sincronizar con el servidor, abrir el WebSocket y mostrar el saludo (una sola vez).
    LaunchedEffect(Unit) {
        viewModel.iniciarConexion()
        saludo?.let { viewModel.mostrarMensaje(it); onSaludoMostrado() }
    }
    // Recibir dinero en tiempo real cuenta como ACTIVIDAD: si la app está abierta mostrando el saldo
    // (ej. en la defensa, mientras se envía desde Postman), la sesión no se cierra por inactividad.
    // collect = se queda escuchando el flujo; cada transferencia recibida llama a onActividad().
    LaunchedEffect(Unit) {
        viewModel.transferenciasRecibidas.collect { onActividad() }
    }
    // Al SALIR de esta pantalla (cerrar sesión, cambiar de usuario): cerrar el WebSocket.
    // DisposableEffect = efecto con "limpieza": onDispose se ejecuta cuando la pantalla desaparece.
    DisposableEffect(Unit) {
        onDispose { viewModel.detenerTiempoReal() }
    }

    // Mensajes de resultado (verde = éxito, rojo = error).
    // LaunchedEffect(estado): se vuelve a lanzar CADA VEZ que cambia "estado".
    // showSnackbar es suspend: espera a que el mensaje desaparezca; luego mensajeMostrado()
    // regresa el estado a Inactivo para no repetir el mismo mensaje.
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
            EstadoOperacion.Inactivo, EstadoOperacion.Procesando -> Unit   // Nada que mostrar.
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
        return                                  // "return" = no dibujar nada más (ni el dashboard).
    }

    // Scaffold = esqueleto Material: contenido + lugar para el Snackbar (mensajes de abajo).
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = {
            // Snackbar personalizado: fondo rojo o verde según mensajeEsError, texto blanco.
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
            // Mientras una operación espera al servidor: barra de progreso arriba y botones desactivados.
            val procesando = estado is EstadoOperacion.Procesando
            if (procesando) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            // El Dashboard solo recibe DATOS y lambdas: no conoce al ViewModel.
            DashboardScreen(
                nombre = sesion.nombre,
                usuario = sesion.usuario,
                cuenta = sesion.cuenta,
                saldo = saldo,
                estadoConexion = estadoConexion,
                movimientos = historial,
                procesando = procesando,
                onAccionClick = { tipo -> dialogoAbierto = tipo },        // Abre el diálogo de esa operación.
                onCerrarSesion = onCerrarSesion,
                onAbrirPruebas = { mostrarPruebas = true },
                esLaboratorio = sesion.usuario == "laboratorio",          // Solo este usuario ve pruebas y nodos.
                nodosConectados = nodosConectados,
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    // Diálogo de operación: solo se dibuja si hay una operación elegida (dialogoAbierto no es null).
    dialogoAbierto?.let { tipo ->
        OperacionDialog(
            tipo = tipo,
            saldo = saldo,
            estadoConexion = estadoConexion,
            cuentaPropia = sesion.cuenta,
            // Referencia a la función suspend del ViewModel: el diálogo la llama y espera la factura.
            onConsultarFactura = viewModel::consultarFactura,
            onActividad = onActividad,
            // Confirmar: se cierra el diálogo y el ViewModel ejecuta la operación en segundo plano.
            onConfirmar = { solicitud ->
                dialogoAbierto = null
                viewModel.ejecutarOperacion(solicitud)
            },
            onCancelar = { dialogoAbierto = null }
        )
    }
}
