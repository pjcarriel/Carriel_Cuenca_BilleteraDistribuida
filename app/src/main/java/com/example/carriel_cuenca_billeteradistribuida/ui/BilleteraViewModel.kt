// Paquete de la interfaz gráfica.
package com.example.carriel_cuenca_billeteradistribuida.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.carriel_cuenca_billeteradistribuida.data.BilleteraRepository
import com.example.carriel_cuenca_billeteradistribuida.data.HTTP_NO_AUTORIZADO
import com.example.carriel_cuenca_billeteradistribuida.data.Resultado
import com.example.carriel_cuenca_billeteradistribuida.data.local.BilleteraDatabase
import com.example.carriel_cuenca_billeteradistribuida.data.remote.RetrofitClient
import com.example.carriel_cuenca_billeteradistribuida.data.remote.WebSocketCliente
import com.example.carriel_cuenca_billeteradistribuida.model.EstadoConexion
import com.example.carriel_cuenca_billeteradistribuida.model.EstadoOperacion
import com.example.carriel_cuenca_billeteradistribuida.model.Factura
import com.example.carriel_cuenca_billeteradistribuida.model.Movimiento
import com.example.carriel_cuenca_billeteradistribuida.model.Solicitud
import com.example.carriel_cuenca_billeteradistribuida.util.logHilo
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// VIEWMODEL DE LA BILLETERA de UNA cuenta (la de la sesión activa).
// "onSesionInvalida": lambda que avisa al SesionViewModel si el servidor rechaza la sesión (401).
class BilleteraViewModel(
    private val repositorio: BilleteraRepository,
    private val onSesionInvalida: (String) -> Unit
) : ViewModel() {

    // ---------------- ESTADO QUE OBSERVA LA PANTALLA ----------------
    val historial: StateFlow<List<Movimiento>> = repositorio.observarHistorial()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val saldo: StateFlow<Double?> = repositorio.observarSaldo()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val estadoConexion: StateFlow<EstadoConexion> = repositorio.estadoConexion

    private val _estado = MutableStateFlow<EstadoOperacion>(EstadoOperacion.Inactivo)
    val estado: StateFlow<EstadoOperacion> = _estado.asStateFlow()

    // Corrutina del WebSocket (null si no está corriendo).
    private var trabajoTiempoReal: Job? = null

    init {
        // Aviso de dinero recibido por WebSocket.
        viewModelScope.launch {
            repositorio.transferenciasRecibidas.collect { movimiento ->
                logHilo("ViewModel: transferencia recibida en tiempo real")
                _estado.value = EstadoOperacion.Exito("Recibiste ${formatearMonto(movimiento.monto)} (${movimiento.detalle})")
            }
        }
        // El WebSocket fue rechazado por la sesión: avisamos al SesionViewModel.
        viewModelScope.launch {
            repositorio.sesionRechazada.collect { mensaje -> onSesionInvalida(mensaje) }
        }
    }

    // ---------------- ACCIONES ----------------

    // Sincroniza saldo e historial con el servidor y abre el WebSocket.
    fun iniciarConexion() {
        viewModelScope.launch {
            logHilo("ViewModel: sincronizando saldo e historial con el servidor")
            when (val resultado = repositorio.sincronizarSaldo()) {
                is Resultado.Exito -> repositorio.sincronizarHistorial()
                    .let { if (it is Resultado.Error) manejarError(it, mostrar = false) }
                is Resultado.Error -> manejarError(resultado)
            }
        }
        if (trabajoTiempoReal?.isActive != true) {
            trabajoTiempoReal = viewModelScope.launch { repositorio.mantenerTiempoReal() }
        }
    }

    // Cierra el WebSocket (al cerrar sesión o al salir de la pantalla).
    fun detenerTiempoReal() {
        trabajoTiempoReal?.cancel()
        trabajoTiempoReal = null
    }

    // Muestra un mensaje de éxito (ej. el saludo de bienvenida).
    fun mostrarMensaje(texto: String) {
        _estado.value = EstadoOperacion.Exito(texto)
    }

    // Consulta una factura. Es "suspend": el diálogo la llama y ESPERA la respuesta sin bloquear.
    suspend fun consultarFactura(servicio: String, codigo: String): Resultado<Factura> {
        val resultado = repositorio.consultarFactura(servicio, codigo)
        if (resultado is Resultado.Error && resultado.codigoHttp == HTTP_NO_AUTORIZADO) onSesionInvalida(resultado.mensaje)
        return resultado
    }

    // Envía una operación al servidor (al confirmar un diálogo).
    fun ejecutarOperacion(solicitud: Solicitud) {
        if (_estado.value is EstadoOperacion.Procesando) return
        viewModelScope.launch {
            logHilo("ViewModel: enviando ${solicitud.tipo.etiqueta} de ${solicitud.monto}")
            _estado.value = EstadoOperacion.Procesando
            when (val resultado = repositorio.ejecutar(solicitud)) {
                is Resultado.Exito -> _estado.value = EstadoOperacion.Exito(resultado.datos.mensaje)
                is Resultado.Error -> manejarError(resultado)
            }
        }
    }

    fun mensajeMostrado() {
        _estado.value = EstadoOperacion.Inactivo
    }

    // 401 = la sesión ya no vale -> volver al login. Cualquier otro error -> mensaje rojo.
    private fun manejarError(error: Resultado.Error, mostrar: Boolean = true) {
        when {
            error.codigoHttp == HTTP_NO_AUTORIZADO -> {
                _estado.value = EstadoOperacion.Inactivo
                onSesionInvalida(error.mensaje)
            }
            mostrar -> _estado.value = EstadoOperacion.Error(error.mensaje)
            else -> _estado.value = EstadoOperacion.Inactivo
        }
    }

    // ---------------- FÁBRICA (construcción manual, sin Hilt) ----------------
    companion object {
        // Recibe la CUENTA de la sesión: cada usuario tiene su propio ViewModel y Repository.
        fun factory(cuenta: String, onSesionInvalida: (String) -> Unit): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                val db = BilleteraDatabase.obtener(app)
                val repositorio = BilleteraRepository(
                    movimientoDao = db.movimientoDao(),
                    cuentaDao = db.cuentaDao(),
                    api = RetrofitClient.api,
                    webSocket = WebSocketCliente(RetrofitClient.clienteHttp),
                    cuenta = cuenta
                )
                BilleteraViewModel(repositorio, onSesionInvalida)
            }
        }
    }
}
