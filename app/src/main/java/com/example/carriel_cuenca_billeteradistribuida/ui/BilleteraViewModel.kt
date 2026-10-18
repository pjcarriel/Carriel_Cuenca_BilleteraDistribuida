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
import com.example.carriel_cuenca_billeteradistribuida.model.EstadoPruebas
import com.example.carriel_cuenca_billeteradistribuida.model.Factura
import com.example.carriel_cuenca_billeteradistribuida.model.Movimiento
import com.example.carriel_cuenca_billeteradistribuida.model.Solicitud
import com.example.carriel_cuenca_billeteradistribuida.model.TipoPrueba
import com.example.carriel_cuenca_billeteradistribuida.util.logHilo
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

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

    // ---------------- PANEL DE PRUEBAS DE CONCURRENCIA ----------------
    private val _pruebas = MutableStateFlow(EstadoPruebas())
    val pruebas: StateFlow<EstadoPruebas> = _pruebas.asStateFlow()

    // Lanza N peticiones AL MISMO TIEMPO (red o Room) y registra qué hilo atendió cada una.
    // Demuestra: red/BD en Dispatchers.IO (varios worker), respuestas de vuelta en main, y
    // que la UI NO se congela (el reloj de la pantalla sigue corriendo mientras esto trabaja).
    fun lanzarConcurrentes(n: Int, tipo: TipoPrueba) {
        if (_pruebas.value.ejecutando) return
        _pruebas.value = EstadoPruebas(ejecutando = true, total = n)
        viewModelScope.launch {                                   // Corrutina en el hilo main.
            val t0 = System.currentTimeMillis()
            val hilos = ConcurrentHashMap<String, Int>()          // Hilo IO -> nº de peticiones.
            val completadas = AtomicInteger(0)
            // Creamos N corrutinas hijas que se ejecutan EN PARALELO.
            val trabajos = (1..n).map {
                launch {
                    // Cada una salta a Dispatchers.IO dentro del Repository (red o Room).
                    val r = when (tipo) {
                        TipoPrueba.SERVIDOR -> repositorio.consultaServidorEnHilo()
                        TipoPrueba.ROOM -> repositorio.lecturaRoomEnHilo()
                    }
                    hilos.merge(r.hilo, 1, Int::plus)             // Contamos el hilo IO que la atendió.
                    val hechas = completadas.incrementAndGet()
                    // Este bloque corre de vuelta en main: actualizar la UI SIEMPRE en el hilo principal.
                    _pruebas.value = _pruebas.value.copy(
                        completadas = hechas,
                        hilos = hilos.toSortedMap(),
                        hiloRespuesta = Thread.currentThread().name
                    )
                }
            }
            trabajos.joinAll()                                    // Esperamos a que terminen todas.
            _pruebas.value = _pruebas.value.copy(ejecutando = false, tiempoMs = System.currentTimeMillis() - t0)
            logHilo("Pruebas: $n peticiones ($tipo) atendidas por ${hilos.size} hilos de Dispatchers.IO")
        }
    }

    // Lanza N TRANSFERENCIAS simultáneas a una cuenta (demo de dos pantallas del usuario laboratorio).
    // En la otra pantalla (la cuenta destino) se ven llegar en tiempo real por WebSocket.
    fun lanzarTransferencias(n: Int, destino: String, monto: Double) {
        if (_pruebas.value.ejecutando) return
        _pruebas.value = EstadoPruebas(ejecutando = true, total = n)
        viewModelScope.launch {
            val t0 = System.currentTimeMillis()
            val hilos = ConcurrentHashMap<String, Int>()
            val completadas = AtomicInteger(0)
            val fallidas = AtomicInteger(0)
            val trabajos = (1..n).map {
                launch {
                    val r = repositorio.transferenciaEnHilo(destino, monto)
                    hilos.merge(r.hilo, 1, Int::plus)
                    if (!r.exito) fallidas.incrementAndGet()
                    val hechas = completadas.incrementAndGet()
                    _pruebas.value = _pruebas.value.copy(
                        completadas = hechas,
                        hilos = hilos.toSortedMap(),
                        hiloRespuesta = Thread.currentThread().name,
                        fallidas = fallidas.get()
                    )
                }
            }
            trabajos.joinAll()
            _pruebas.value = _pruebas.value.copy(ejecutando = false, tiempoMs = System.currentTimeMillis() - t0)
            logHilo("Pruebas: $n transferencias a $destino en ${hilos.size} hilos IO (${fallidas.get()} fallidas)")
        }
    }

    // CONTRASTE (lo que NO se debe hacer): bloquea el hilo main a propósito.
    // La UI se congela unos segundos; sirve para explicar por qué el trabajo pesado va en IO.
    // No usa corrutina: se llama directo desde el botón, que corre en main.
    fun bloquearMainAPosta(segundos: Int) {
        logHilo("Pruebas: BLOQUEANDO el hilo main ${segundos}s (demostración de un ANR)")
        Thread.sleep(segundos * 1000L)
    }

    fun limpiarPruebas() {
        _pruebas.value = EstadoPruebas()
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
