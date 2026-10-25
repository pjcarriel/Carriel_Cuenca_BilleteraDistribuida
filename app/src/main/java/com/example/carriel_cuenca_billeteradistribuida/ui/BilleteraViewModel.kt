// =====================================================================
//   Es el CEREBRO de la billetera: cuando el usuario aprieta un botón,
//   decide qué hacer (depositar, pagar, mostrar mensaje, abrir comprobante).
// =====================================================================

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
import com.example.carriel_cuenca_billeteradistribuida.model.EstadoAdmin
import com.example.carriel_cuenca_billeteradistribuida.model.EstadoConexion
import com.example.carriel_cuenca_billeteradistribuida.model.EstadoOperacion
import com.example.carriel_cuenca_billeteradistribuida.model.EstadoPruebas
import com.example.carriel_cuenca_billeteradistribuida.model.Factura
import com.example.carriel_cuenca_billeteradistribuida.model.Movimiento
import com.example.carriel_cuenca_billeteradistribuida.model.NodosConectados
import com.example.carriel_cuenca_billeteradistribuida.model.SERVICIOS_BASE
import com.example.carriel_cuenca_billeteradistribuida.model.Servicio
import com.example.carriel_cuenca_billeteradistribuida.model.Solicitud
import com.example.carriel_cuenca_billeteradistribuida.ui.components.CatalogoServicios
import com.example.carriel_cuenca_billeteradistribuida.model.TipoPrueba
import com.example.carriel_cuenca_billeteradistribuida.util.logHilo
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

// Cerebro de la billetera de UNA cuenta (la del usuario que inició sesión).
class BilleteraViewModel(
    private val repositorio: BilleteraRepository,
    private val onSesionInvalida: (String) -> Unit
) : ViewModel() {

    // ---------------- DATOS QUE MUESTRA LA PANTALLA ----------------

    // Historial de movimientos (lista vacía mientras carga).
    val historial: StateFlow<List<Movimiento>> = repositorio.observarHistorial()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Saldo de la cuenta (null = todavía no se sabe).
    val saldo: StateFlow<Double?> = repositorio.observarSaldo()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // En línea, conectando o desconectado.
    val estadoConexion: StateFlow<EstadoConexion> = repositorio.estadoConexion

    // Dispositivos conectados (solo para laboratorio).
    val nodosConectados: StateFlow<NodosConectados?> = repositorio.nodosConectados

    // Aviso de "llegó dinero" en tiempo real.
    val transferenciasRecibidas: SharedFlow<Movimiento> = repositorio.transferenciasRecibidas

    // Resultado de la última operación: inactivo, procesando, éxito o error.
    private val _estado = MutableStateFlow<EstadoOperacion>(EstadoOperacion.Inactivo)
    val estado: StateFlow<EstadoOperacion> = _estado.asStateFlow()

    // Conexión en tiempo real (null = apagada).
    private var trabajoTiempoReal: Job? = null

    // Al crearse, se queda escuchando tres avisos del servidor.
    init {
        // Llegó dinero: mensaje verde "Recibiste $...".
        viewModelScope.launch {
            repositorio.transferenciasRecibidas.collect { movimiento ->
                logHilo("ViewModel: transferencia recibida en tiempo real")
                _estado.value = EstadoOperacion.Exito("Recibiste ${formatearMonto(movimiento.monto)} (${movimiento.detalle})")
            }
        }
        // Cambió una factura: si el panel de facturas está abierto, se recarga.
        viewModelScope.launch {
            repositorio.facturasActualizadas.collect { if (_admin.value.abierto) cargarFacturasAdmin() }
        }
        // El servidor rechazó la sesión: volver al login.
        viewModelScope.launch {
            repositorio.sesionRechazada.collect { mensaje -> onSesionInvalida(mensaje) }
        }
    }

    // ---------------- ACCIONES ----------------

    // Al entrar: trae saldo e historial y abre la conexión en tiempo real.
    fun iniciarConexion() {
        cargarServicios()
        viewModelScope.launch {
            logHilo("ViewModel: sincronizando saldo e historial con el servidor")
            when (val resultado = repositorio.sincronizarSaldo()) {
                is Resultado.Exito -> repositorio.sincronizarHistorial()
                    .let { if (it is Resultado.Error) manejarError(it, mostrar = false) }
                is Resultado.Error -> manejarError(resultado)
            }
        }
        // Solo una conexión a la vez.
        if (trabajoTiempoReal?.isActive != true) {
            trabajoTiempoReal = viewModelScope.launch { repositorio.mantenerTiempoReal() }
        }
    }

    // Al cerrar sesión: corta la conexión en tiempo real.
    fun detenerTiempoReal() {
        trabajoTiempoReal?.cancel()
        trabajoTiempoReal = null
    }

    // Muestra abajo un mensaje verde (ej. el saludo).
    fun mostrarMensaje(texto: String) {
        _estado.value = EstadoOperacion.Exito(texto)
    }

    // Busca una factura por número de contrato (en "Pagar servicio").
    suspend fun consultarFactura(servicio: String, codigo: String): Resultado<Factura> {
        val resultado = repositorio.consultarFactura(servicio, codigo)
        if (resultado is Resultado.Error && resultado.codigoHttp == HTTP_NO_AUTORIZADO) onSesionInvalida(resultado.mensaje)
        return resultado
    }

    // Busca las facturas pendientes de una cédula (pago "Por cédula").
    suspend fun buscarFacturas(cedula: String, servicio: String): Resultado<List<Factura>> {
        val resultado = repositorio.buscarFacturas(cedula, servicio)
        if (resultado is Resultado.Error && resultado.codigoHttp == HTTP_NO_AUTORIZADO) onSesionInvalida(resultado.mensaje)
        return resultado
    }

    // ---------------- CATÁLOGO DE SERVICIOS ----------------
    // Servicios que se pueden pagar (empieza con los 3 básicos).
    private val _servicios = MutableStateFlow(SERVICIOS_BASE)
    val servicios: StateFlow<List<Servicio>> = _servicios.asStateFlow()

    // Trae del servidor la lista de servicios (Luz, Agua, Internet...).
    fun cargarServicios() {
        viewModelScope.launch {
            val r = repositorio.servicios()
            if (r is Resultado.Exito && r.datos.isNotEmpty()) {
                _servicios.value = r.datos
                CatalogoServicios.actualizar(r.datos)           // Para los íconos del historial.
            }
        }
    }

    // El laboratorio crea un servicio nuevo; luego se recarga la lista.
    suspend fun crearServicio(codigo: String, nombre: String, icono: String): Resultado<Servicio> {
        val r = repositorio.crearServicio(codigo, nombre, icono)
        if (r is Resultado.Exito) cargarServicios()
        if (r is Resultado.Error && r.codigoHttp == HTTP_NO_AUTORIZADO) onSesionInvalida(r.mensaje)
        return r
    }

    // ---------------- PANEL DEL ADMINISTRADOR (laboratorio) ----------------
    // Facturas, filtros y estado de carga del panel.
    private val _admin = MutableStateFlow(EstadoAdmin())
    val admin: StateFlow<EstadoAdmin> = _admin.asStateFlow()

    // Avisos en tiempo real ("La cuenta 1001 pagó LUZ 445568").
    val avisosFacturas: SharedFlow<String> = repositorio.facturasActualizadas

    // Trae la lista de facturas con los filtros elegidos.
    fun cargarFacturasAdmin(estado: String? = _admin.value.estado, servicio: String? = _admin.value.servicio) {
        _admin.value = _admin.value.copy(cargando = true, estado = estado, servicio = servicio, error = null, abierto = true)
        viewModelScope.launch {
            when (val r = repositorio.adminFacturas(estado, servicio)) {
                is Resultado.Exito -> _admin.value = _admin.value.copy(cargando = false, facturas = r.datos)
                is Resultado.Error -> {
                    _admin.value = _admin.value.copy(cargando = false, error = r.mensaje)
                    if (r.codigoHttp == HTTP_NO_AUTORIZADO) onSesionInvalida(r.mensaje)
                }
            }
        }
    }

    // El administrador salió del panel: ya no se recarga la lista.
    fun cerrarAdmin() {
        _admin.value = _admin.value.copy(abierto = false)
    }

    // El laboratorio emite una factura nueva; luego se recarga la lista.
    suspend fun crearFactura(cedula: String, titular: String, servicio: String, periodo: String, monto: Double): Resultado<Factura> {
        val r = repositorio.crearFactura(cedula, titular, servicio, periodo, monto)
        if (r is Resultado.Exito) cargarFacturasAdmin()
        if (r is Resultado.Error && r.codigoHttp == HTTP_NO_AUTORIZADO) onSesionInvalida(r.mensaje)
        return r
    }

    // El usuario aprieta Confirmar: se envía la operación y sale mensaje verde o rojo.
    fun ejecutarOperacion(solicitud: Solicitud) {
        // Si ya hay una en curso, se ignora (no se envía dos veces).
        if (_estado.value is EstadoOperacion.Procesando) return
        viewModelScope.launch {
            logHilo("ViewModel: enviando ${solicitud.tipo.etiqueta} de ${solicitud.monto}")
            _estado.value = EstadoOperacion.Procesando
            when (val resultado = repositorio.ejecutar(solicitud)) {
                is Resultado.Exito -> {
                    _estado.value = EstadoOperacion.Exito(resultado.datos.mensaje)
                    // Si salió bien, se abre el comprobante.
                    _comprobanteNuevo.value = resultado.datos.movimiento
                }
                is Resultado.Error -> manejarError(resultado)
            }
        }
    }

    // ---------------- COMPROBANTES ----------------
    // Comprobante que se abre solo tras una operación exitosa (null = ninguno).
    private val _comprobanteNuevo = MutableStateFlow<Movimiento?>(null)
    val comprobanteNuevo: StateFlow<Movimiento?> = _comprobanteNuevo.asStateFlow()

    // El comprobante ya se abrió: se borra para que no se repita.
    fun comprobanteMostrado() {
        _comprobanteNuevo.value = null
    }

    // El mensaje ya se mostró: se borra para que no se repita.
    fun mensajeMostrado() {
        _estado.value = EstadoOperacion.Inactivo
    }

    // ---------------- PANEL DE PRUEBAS DE CONCURRENCIA ----------------
    // Resultados de las pruebas (cuántas terminaron, qué hilos, tiempo).
    private val _pruebas = MutableStateFlow(EstadoPruebas())
    val pruebas: StateFlow<EstadoPruebas> = _pruebas.asStateFlow()

    // Lanza N consultas a la vez y muestra qué hilo atendió cada una.
    fun lanzarConcurrentes(n: Int, tipo: TipoPrueba) {
        if (_pruebas.value.ejecutando) return
        _pruebas.value = EstadoPruebas(ejecutando = true, total = n)
        viewModelScope.launch {
            val t0 = System.currentTimeMillis()
            // Contadores seguros aunque muchas tareas los usen a la vez.
            val hilos = ConcurrentHashMap<String, Int>()
            val completadas = AtomicInteger(0)
            // N tareas en paralelo.
            val trabajos = (1..n).map {
                launch {
                    val r = when (tipo) {
                        TipoPrueba.SERVIDOR -> repositorio.consultaServidorEnHilo()
                        TipoPrueba.ROOM -> repositorio.lecturaRoomEnHilo()
                    }
                    hilos.merge(r.hilo, 1, Int::plus)             // +1 al hilo que la atendió.
                    val hechas = completadas.incrementAndGet()
                    _pruebas.value = _pruebas.value.copy(
                        completadas = hechas,
                        hilos = hilos.toSortedMap(),
                        hiloRespuesta = Thread.currentThread().name
                    )
                }
            }
            trabajos.joinAll()                                    // Esperar a que terminen todas.
            _pruebas.value = _pruebas.value.copy(ejecutando = false, tiempoMs = System.currentTimeMillis() - t0)
            logHilo("Pruebas: $n peticiones ($tipo) atendidas por ${hilos.size} hilos de Dispatchers.IO")
        }
    }

    // Envía N transferencias a la vez a otra cuenta (se ven llegar en el otro teléfono).
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

    // Lo que NO se debe hacer: congela la app unos segundos a propósito.
    fun bloquearMainAPosta(segundos: Int) {
        logHilo("Pruebas: BLOQUEANDO el hilo main ${segundos}s (demostración de un ANR)")
        Thread.sleep(segundos * 1000L)
    }

    // Si algo falla: sesión vencida (401) → volver al login; otro error → mensaje rojo.
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

    // ---------------- FÁBRICA ----------------
    companion object {
        // Le dice a Android cómo crear este ViewModel para una cuenta.
        fun factory(cuenta: String, onSesionInvalida: (String) -> Unit): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                val db = BilleteraDatabase.obtener(app)
                // Repository con la base de datos, la conexión en tiempo real y la cuenta.
                val repositorio = BilleteraRepository(
                    movimientoDao = db.movimientoDao(),
                    cuentaDao = db.cuentaDao(),
                    webSocket = WebSocketCliente(RetrofitClient.clienteHttp),
                    cuenta = cuenta
                )
                BilleteraViewModel(repositorio, onSesionInvalida)
            }
        }
    }
}
