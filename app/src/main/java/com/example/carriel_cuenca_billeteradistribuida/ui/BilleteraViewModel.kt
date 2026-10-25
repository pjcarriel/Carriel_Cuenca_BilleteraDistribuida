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

// VIEWMODEL DE LA BILLETERA de UNA cuenta (la de la sesión activa).
// "onSesionInvalida": lambda que avisa al SesionViewModel si el servidor rechaza la sesión (401).
class BilleteraViewModel(
    private val repositorio: BilleteraRepository,
    private val onSesionInvalida: (String) -> Unit
) : ViewModel() {

    // ---------------- ESTADO QUE OBSERVA LA PANTALLA ----------------
    // stateIn convierte el Flow de Room en un StateFlow (con valor actual) que la pantalla puede leer.
    //  - viewModelScope: ámbito de corrutinas del ViewModel; se CANCELA solo cuando el ViewModel muere.
    //  - SharingStarted.WhileSubscribed(5_000): lee Room solo mientras alguien observa; si la pantalla
    //    deja de observar, espera 5 s antes de detenerse (así una rotación de pantalla no reinicia la lectura).
    //  - El último parámetro es el valor INICIAL mientras llega el primer dato (lista vacía / null).
    val historial: StateFlow<List<Movimiento>> = repositorio.observarHistorial()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val saldo: StateFlow<Double?> = repositorio.observarSaldo()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val estadoConexion: StateFlow<EstadoConexion> = repositorio.estadoConexion

    // Nodos conectados al servidor (solo se llena para el usuario laboratorio).
    val nodosConectados: StateFlow<NodosConectados?> = repositorio.nodosConectados

    // Evento "llegó dinero por WebSocket" (la pantalla lo usa para reiniciar el contador de inactividad).
    val transferenciasRecibidas: SharedFlow<Movimiento> = repositorio.transferenciasRecibidas

    // Estado de la última operación (Inactivo, Procesando, Exito, Error). "_estado" solo se cambia aquí.
    private val _estado = MutableStateFlow<EstadoOperacion>(EstadoOperacion.Inactivo)
    val estado: StateFlow<EstadoOperacion> = _estado.asStateFlow()

    // Corrutina del WebSocket (null si no está corriendo).
    // Job = el "control remoto" de una corrutina lanzada: permite preguntar isActive o llamar cancel().
    private var trabajoTiempoReal: Job? = null

    // "init" corre al crear el ViewModel. Cada launch es una corrutina que se queda ESCUCHANDO un
    // SharedFlow (collect no termina) hasta que el ViewModel se destruye y viewModelScope la cancela.
    init {
        // Aviso de dinero recibido por WebSocket.
        viewModelScope.launch {
            repositorio.transferenciasRecibidas.collect { movimiento ->
                logHilo("ViewModel: transferencia recibida en tiempo real")
                _estado.value = EstadoOperacion.Exito("Recibiste ${formatearMonto(movimiento.monto)} (${movimiento.detalle})")
            }
        }
        // Alguien pagó o se creó una factura: si el panel de servicios está abierto, se recarga solo.
        viewModelScope.launch {
            repositorio.facturasActualizadas.collect { if (_admin.value.abierto) cargarFacturasAdmin() }
        }
        // El WebSocket fue rechazado por la sesión: avisamos al SesionViewModel.
        viewModelScope.launch {
            repositorio.sesionRechazada.collect { mensaje -> onSesionInvalida(mensaje) }
        }
    }

    // ---------------- ACCIONES ----------------

    // Sincroniza saldo e historial con el servidor y abre el WebSocket.
    // viewModelScope.launch inicia la corrutina en el hilo MAIN; el Repository salta a IO por dentro.
    fun iniciarConexion() {
        cargarServicios()                                       // Catálogo de servicios (para pagos e íconos).
        viewModelScope.launch {
            logHilo("ViewModel: sincronizando saldo e historial con el servidor")
            when (val resultado = repositorio.sincronizarSaldo()) {
                is Resultado.Exito -> repositorio.sincronizarHistorial()
                    .let { if (it is Resultado.Error) manejarError(it, mostrar = false) }
                is Resultado.Error -> manejarError(resultado)
            }
        }
        // "!= true" cubre dos casos: no hay Job (null) o ya terminó. Así nunca abrimos DOS túneles a la vez.
        if (trabajoTiempoReal?.isActive != true) {
            trabajoTiempoReal = viewModelScope.launch { repositorio.mantenerTiempoReal() }
        }
    }

    // Cierra el WebSocket (al cerrar sesión o al salir de la pantalla).
    // cancel() detiene la corrutina: el delay o el collect lanzan CancellationException, awaitClose
    // (WebSocketCliente) cierra el socket y el bucle de reconexión termina.
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

    // Busca las facturas de una cédula. "suspend" como consultarFactura: el diálogo espera la lista.
    suspend fun buscarFacturas(cedula: String, servicio: String): Resultado<List<Factura>> {
        val resultado = repositorio.buscarFacturas(cedula, servicio)
        if (resultado is Resultado.Error && resultado.codigoHttp == HTTP_NO_AUTORIZADO) onSesionInvalida(resultado.mensaje)
        return resultado
    }

    // ---------------- CATÁLOGO DE SERVICIOS ----------------
    // Empieza con los 3 de siempre (por si no hay conexión) y se reemplaza con el del servidor.
    private val _servicios = MutableStateFlow(SERVICIOS_BASE)
    val servicios: StateFlow<List<Servicio>> = _servicios.asStateFlow()

    // Descarga el catálogo (al entrar y después de crear un servicio).
    fun cargarServicios() {
        viewModelScope.launch {
            val r = repositorio.servicios()
            if (r is Resultado.Exito && r.datos.isNotEmpty()) {
                _servicios.value = r.datos
                CatalogoServicios.actualizar(r.datos)           // Para los íconos del historial.
            }
        }
    }

    // Agrega un servicio (solo laboratorio). Si sale bien, recarga el catálogo.
    suspend fun crearServicio(codigo: String, nombre: String, icono: String): Resultado<Servicio> {
        val r = repositorio.crearServicio(codigo, nombre, icono)
        if (r is Resultado.Exito) cargarServicios()
        if (r is Resultado.Error && r.codigoHttp == HTTP_NO_AUTORIZADO) onSesionInvalida(r.mensaje)
        return r
    }

    // ---------------- ADMINISTRACIÓN DE SERVICIOS (solo laboratorio) ----------------
    private val _admin = MutableStateFlow(EstadoAdmin())
    val admin: StateFlow<EstadoAdmin> = _admin.asStateFlow()

    // Avisos en tiempo real ("La cuenta 1001 pagó LUZ 445568"): la pantalla los muestra en un Snackbar.
    val avisosFacturas: SharedFlow<String> = repositorio.facturasActualizadas

    // Carga la lista con los filtros actuales. Si se pasan filtros nuevos, primero los guarda.
    // "copy" crea un EstadoAdmin igual pero con los campos indicados cambiados.
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

    // El panel se cerró: ya no hace falta recargar con cada aviso.
    fun cerrarAdmin() {
        _admin.value = _admin.value.copy(abierto = false)
    }

    // Crea una factura. Si sale bien, recarga la lista para que aparezca.
    suspend fun crearFactura(cedula: String, titular: String, servicio: String, periodo: String, monto: Double): Resultado<Factura> {
        val r = repositorio.crearFactura(cedula, titular, servicio, periodo, monto)
        if (r is Resultado.Exito) cargarFacturasAdmin()
        if (r is Resultado.Error && r.codigoHttp == HTTP_NO_AUTORIZADO) onSesionInvalida(r.mensaje)
        return r
    }

    // Envía una operación al servidor (al confirmar un diálogo).
    fun ejecutarOperacion(solicitud: Solicitud) {
        // Si ya hay una operación en curso, se ignora el nuevo toque (evita enviar dos veces la misma).
        if (_estado.value is EstadoOperacion.Procesando) return
        viewModelScope.launch {
            logHilo("ViewModel: enviando ${solicitud.tipo.etiqueta} de ${solicitud.monto}")
            _estado.value = EstadoOperacion.Procesando
            when (val resultado = repositorio.ejecutar(solicitud)) {
                is Resultado.Exito -> {
                    _estado.value = EstadoOperacion.Exito(resultado.datos.mensaje)
                    // Como en los bancos: al terminar bien, se muestra el COMPROBANTE de la operación.
                    _comprobanteNuevo.value = resultado.datos.movimiento
                }
                is Resultado.Error -> manejarError(resultado)
            }
        }
    }

    // ---------------- COMPROBANTES ----------------
    // Comprobante que la pantalla debe abrir sola tras una operación exitosa (null = ninguno).
    private val _comprobanteNuevo = MutableStateFlow<Movimiento?>(null)
    val comprobanteNuevo: StateFlow<Movimiento?> = _comprobanteNuevo.asStateFlow()

    // La pantalla ya abrió el comprobante: lo quitamos para no abrirlo otra vez (ej. al rotar).
    fun comprobanteMostrado() {
        _comprobanteNuevo.value = null
    }

    // La pantalla ya mostró el mensaje: volvemos a Inactivo para no mostrarlo otra vez.
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
            // Estructuras SEGURAS ENTRE HILOS: muchas corrutinas las tocan a la vez.
            // AtomicInteger.incrementAndGet() suma 1 de forma atómica (nunca se pierde una cuenta,
            // a diferencia de "n++" con un Int normal).
            val hilos = ConcurrentHashMap<String, Int>()          // Hilo IO -> nº de peticiones.
            val completadas = AtomicInteger(0)
            // Creamos N corrutinas hijas que se ejecutan EN PARALELO.
            // map devuelve la lista de Job de cada launch, para luego esperarlos con joinAll().
            val trabajos = (1..n).map {
                launch {
                    // Cada una salta a Dispatchers.IO dentro del Repository (red o Room).
                    val r = when (tipo) {
                        TipoPrueba.SERVIDOR -> repositorio.consultaServidorEnHilo()
                        TipoPrueba.ROOM -> repositorio.lecturaRoomEnHilo()
                    }
                    // merge: si el hilo no está, lo pone con 1; si ya está, le suma 1 (Int::plus).
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

    // Deja el panel de pruebas en su estado inicial (sin resultados).
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
        // Android crea los ViewModel por nosotros (para que sobrevivan a la rotación); la FÁBRICA le
        // dice CÓMO construir este, que necesita parámetros. Aquí se arma a mano: base de datos -> DAOs -> Repository.
        fun factory(cuenta: String, onSesionInvalida: (String) -> Unit): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                // APPLICATION_KEY entrega la Application (contexto de toda la app); checkNotNull falla si no está.
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                val db = BilleteraDatabase.obtener(app)
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
