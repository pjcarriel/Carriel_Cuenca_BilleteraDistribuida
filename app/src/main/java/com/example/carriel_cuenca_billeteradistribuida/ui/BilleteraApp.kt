// =====================================================================
//   Es la billetera por dentro, cuando el usuario YA inició sesión: decide
//   qué pantalla, ventana, mensaje o comprobante se muestra en cada momento.
// =====================================================================

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
import androidx.compose.runtime.DisposableEffect
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
import com.example.carriel_cuenca_billeteradistribuida.model.Movimiento
import com.example.carriel_cuenca_billeteradistribuida.model.TipoOperacion
import com.example.carriel_cuenca_billeteradistribuida.ui.admin.AdminServiciosScreen
import com.example.carriel_cuenca_billeteradistribuida.ui.admin.LaboratorioScreen
import com.example.carriel_cuenca_billeteradistribuida.ui.components.ComprobanteDialog
import com.example.carriel_cuenca_billeteradistribuida.ui.components.DetalleMovimientoSheet
import com.example.carriel_cuenca_billeteradistribuida.ui.components.OperacionDialog
import com.example.carriel_cuenca_billeteradistribuida.ui.pruebas.PruebasConcurrenciaScreen
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.RojoEgreso
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.VerdeIngreso

// Todo lo que el usuario ve después de iniciar sesión.
@Composable
fun BilleteraApp(
    sesion: SesionEntity,                       // Quién inició sesión (usuario, nombre, cuenta).
    saludo: String?,                            // Mensaje de bienvenida (o null).
    onSaludoMostrado: () -> Unit,               // Avisa que el saludo ya se mostró.
    onCerrarSesion: () -> Unit,                 // Botón de cerrar sesión.
    onActividad: () -> Unit,                    // Reinicia el contador de inactividad.
    onSesionInvalida: (String) -> Unit,         // El servidor ya no acepta la sesión.
    // Un ViewModel por cuenta, para que no se mezclen los datos de dos usuarios.
    viewModel: BilleteraViewModel = viewModel(
        key = "billetera-${sesion.cuenta}",
        factory = BilleteraViewModel.factory(sesion.cuenta, onSesionInvalida)
    )
) {
    // Datos del ViewModel; la pantalla se actualiza sola cuando cambian.
    val saldo by viewModel.saldo.collectAsStateWithLifecycle()
    val estadoConexion by viewModel.estadoConexion.collectAsStateWithLifecycle()
    val historial by viewModel.historial.collectAsStateWithLifecycle()
    val estado by viewModel.estado.collectAsStateWithLifecycle()                   // Resultado de la última operación.
    val nodosConectados by viewModel.nodosConectados.collectAsStateWithLifecycle()
    val servicios by viewModel.servicios.collectAsStateWithLifecycle()
    val admin by viewModel.admin.collectAsStateWithLifecycle()                         // Facturas (laboratorio).

    // Qué ventana de operación está abierta (null = ninguna).
    var dialogoAbierto by remember { mutableStateOf<TipoOperacion?>(null) }
    var mostrarPruebas by remember { mutableStateOf(false) }      // Panel de pruebas abierto.
    var mostrarServicios by remember { mutableStateOf(false) }    // Panel de servicios abierto.
    // Comprobante abierto (null = ninguno).
    var comprobante by remember { mutableStateOf<Movimiento?>(null) }
    // Movimiento con su detalle abierto (null = ninguno).
    var detalle by remember { mutableStateOf<Movimiento?>(null) }
    val comprobanteNuevo by viewModel.comprobanteNuevo.collectAsStateWithLifecycle()
    // Barrita de mensajes de abajo.
    val snackbarHostState = remember { SnackbarHostState() }
    var mensajeEsError by remember { mutableStateOf(false) }      // true = rojo, false = verde.

    // Al entrar: conectarse al servidor y mostrar el saludo.
    LaunchedEffect(Unit) {
        viewModel.iniciarConexion()
        saludo?.let { viewModel.mostrarMensaje(it); onSaludoMostrado() }
    }
    // Si llega dinero, cuenta como actividad (la sesión no se cierra).
    LaunchedEffect(Unit) {
        viewModel.transferenciasRecibidas.collect { onActividad() }
    }
    // Al salir de la billetera: cortar la conexión en tiempo real.
    DisposableEffect(Unit) {
        onDispose { viewModel.detenerTiempoReal() }
    }

    // Muestra abajo el resultado de cada operación: verde = éxito, rojo = error.
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

    // Operación exitosa: se abre su comprobante.
    LaunchedEffect(comprobanteNuevo) {
        comprobanteNuevo?.let { comprobante = it; viewModel.comprobanteMostrado() }
    }

    // Detalle de un movimiento, con el botón "Ver comprobante".
    detalle?.let { mov ->
        DetalleMovimientoSheet(
            movimiento = mov,
            onVerComprobante = { detalle = null; comprobante = mov },
            onCerrar = { detalle = null }
        )
    }

    // Ventana del comprobante.
    comprobante?.let { mov ->
        ComprobanteDialog(movimiento = mov, cuenta = sesion.cuenta, titular = sesion.nombre, onCerrar = { comprobante = null })
    }

    // Panel de servicios (laboratorio): reemplaza a la pantalla principal.
    if (mostrarServicios) {
        AdminServiciosScreen(
            viewModel = viewModel,
            onVolver = { mostrarServicios = false },
            onActividad = onActividad
        )
        return
    }

    // Panel de pruebas de concurrencia: reemplaza a la pantalla principal.
    if (mostrarPruebas) {
        PruebasConcurrenciaScreen(
            viewModel = viewModel,
            onVolver = { mostrarPruebas = false },
            onActividad = onActividad
        )
        return
    }

    // Estructura de la pantalla: contenido + barrita de mensajes abajo.
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = {
            // Mensaje con fondo rojo o verde y letras blancas.
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
            // Mientras se espera al servidor: barra de carga arriba.
            val procesando = estado is EstadoOperacion.Procesando
            if (procesando) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            // "laboratorio" es el administrador: ve su propio panel, no la billetera de cliente.
            if (sesion.usuario == "laboratorio") {
                LaunchedEffect(Unit) { viewModel.cargarFacturasAdmin(estado = null, servicio = null) }
                LaboratorioScreen(
                    estadoConexion = estadoConexion,
                    nodosConectados = nodosConectados,
                    admin = admin,
                    servicios = servicios,
                    onAbrirServicios = { mostrarServicios = true },
                    onAbrirPruebas = { mostrarPruebas = true },
                    onCerrarSesion = onCerrarSesion,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Pantalla principal del cliente.
                DashboardScreen(
                    nombre = sesion.nombre,
                    usuario = sesion.usuario,
                    cuenta = sesion.cuenta,
                    saldo = saldo,
                    estadoConexion = estadoConexion,
                    movimientos = historial,
                    procesando = procesando,
                    onAccionClick = { tipo -> dialogoAbierto = tipo },    // Abre la ventana de esa operación.
                    onCerrarSesion = onCerrarSesion,
                    onVerDetalle = { detalle = it },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    // Ventana de operación (depósito, retiro, transferencia o pago).
    dialogoAbierto?.let { tipo ->
        OperacionDialog(
            tipo = tipo,
            saldo = saldo,
            estadoConexion = estadoConexion,
            cuentaPropia = sesion.cuenta,
            servicios = servicios,
            onConsultarFactura = viewModel::consultarFactura,
            onBuscarFacturas = viewModel::buscarFacturas,
            onActividad = onActividad,
            // Confirmar: se cierra la ventana y el ViewModel hace la operación.
            onConfirmar = { solicitud ->
                dialogoAbierto = null
                viewModel.ejecutarOperacion(solicitud)
            },
            onCancelar = { dialogoAbierto = null }
        )
    }
}
