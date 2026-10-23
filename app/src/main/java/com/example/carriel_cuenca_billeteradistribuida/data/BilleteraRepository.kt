// Paquete "data": todo lo relacionado con OBTENER y GUARDAR datos.
package com.example.carriel_cuenca_billeteradistribuida.data

import com.example.carriel_cuenca_billeteradistribuida.data.local.CuentaDao
import com.example.carriel_cuenca_billeteradistribuida.data.local.CuentaLocalEntity
import com.example.carriel_cuenca_billeteradistribuida.data.local.MovimientoDao
import com.example.carriel_cuenca_billeteradistribuida.data.local.aEntidad
import com.example.carriel_cuenca_billeteradistribuida.data.local.aModelo
import com.example.carriel_cuenca_billeteradistribuida.data.remote.BilleteraApi
import com.example.carriel_cuenca_billeteradistribuida.data.remote.ConfigServidor
import com.example.carriel_cuenca_billeteradistribuida.data.remote.EventoSocket
import com.example.carriel_cuenca_billeteradistribuida.data.remote.ProveedorToken
import com.example.carriel_cuenca_billeteradistribuida.data.remote.RetrofitClient
import com.example.carriel_cuenca_billeteradistribuida.data.remote.SesionRechazadaException
import com.example.carriel_cuenca_billeteradistribuida.data.remote.WebSocketCliente
import com.example.carriel_cuenca_billeteradistribuida.data.remote.dto.EventoWebSocketDto
import com.example.carriel_cuenca_billeteradistribuida.data.remote.dto.MontoRequest
import com.example.carriel_cuenca_billeteradistribuida.data.remote.dto.OperacionRespuestaDto
import com.example.carriel_cuenca_billeteradistribuida.data.remote.dto.PagoServicioRequest
import com.example.carriel_cuenca_billeteradistribuida.data.remote.dto.TransferenciaRequest
import com.example.carriel_cuenca_billeteradistribuida.model.CATEGORIA_SERVICIO_BASICO
import com.example.carriel_cuenca_billeteradistribuida.model.EstadoConexion
import com.example.carriel_cuenca_billeteradistribuida.model.Factura
import com.example.carriel_cuenca_billeteradistribuida.model.Movimiento
import com.example.carriel_cuenca_billeteradistribuida.model.Solicitud
import com.example.carriel_cuenca_billeteradistribuida.model.TipoOperacion
import com.example.carriel_cuenca_billeteradistribuida.util.logHilo
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers          // Dispatchers.IO = hilos para disco y red.
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

// Tiempo máximo de espera entre reintentos de conexión del WebSocket (10 segundos).
private const val ESPERA_MAXIMA_MS = 10_000L

// Resultado de una operación del panel de pruebas: si salió bien y en qué hilo corrió.
data class ResultadoHilo(val exito: Boolean, val hilo: String)

// REPOSITORIO DE LA BILLETERA de UNA cuenta (la del usuario que inició sesión).
// Combina Room (historial y último saldo), Retrofit (operaciones) y WebSocket (tiempo real).
// REGLA DE ESTE ARCHIVO: todo trabajo de disco/red se hace en Dispatchers.IO.
class BilleteraRepository(
    private val movimientoDao: MovimientoDao,
    private val cuentaDao: CuentaDao,
    private val webSocket: WebSocketCliente,
    private val cuenta: String                       // Cuenta de la SESIÓN (ej. "1001", "1004"...).
) {
    // Se lee en CADA uso (no se guarda fijo): si cambia la dirección del servidor, la siguiente
    // llamada usa la nueva. (Antes se capturaba una vez y se quedaba apuntando a 10.0.2.2.)
    private val api: BilleteraApi get() = RetrofitClient.api

    private val gson = Gson()

    // Estado del túnel WebSocket (indicador verde/ámbar/rojo).
    private val _estadoConexion = MutableStateFlow(EstadoConexion.DESCONECTADO)
    val estadoConexion: StateFlow<EstadoConexion> = _estadoConexion.asStateFlow()

    // Evento: llegó una transferencia (para mostrar "Recibiste $...").
    private val _transferenciasRecibidas = MutableSharedFlow<Movimiento>(extraBufferCapacity = 10)
    val transferenciasRecibidas: SharedFlow<Movimiento> = _transferenciasRecibidas.asSharedFlow()

    // Evento: el servidor rechazó la sesión en el WebSocket (expiró o se cerró en otro lugar).
    private val _sesionRechazada = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val sesionRechazada: SharedFlow<String> = _sesionRechazada.asSharedFlow()

    // ---------------------------- LECTURAS (Flow desde Room) ----------------------------

    // Historial de ESTA cuenta (funciona sin internet).
    fun observarHistorial(): Flow<List<Movimiento>> =
        movimientoDao.observarTodos(cuenta)
            .map { entidades ->
                logHilo("Repository: leyendo historial de Room de la cuenta $cuenta (${entidades.size} filas)")
                entidades.map { it.aModelo() }
            }
            .flowOn(Dispatchers.IO)

    // Último saldo conocido de ESTA cuenta.
    fun observarSaldo(): Flow<Double?> =
        cuentaDao.observarSaldo(cuenta).flowOn(Dispatchers.IO)

    // ---------------------------- SERVIDOR (REST) ----------------------------

    // Pide el saldo actual al servidor y lo guarda en Room.
    suspend fun sincronizarSaldo(): Resultado<Double> = withContext(Dispatchers.IO) {
        llamarServidor {
            logHilo("Repository: GET /mi-cuenta (Retrofit)")
            val datos = api.miCuenta()
            guardarSaldo(datos.saldo)
            datos.saldo
        }
    }

    // Descarga el historial del servidor y lo guarda en Room (los repetidos se ignoran).
    // Así, al iniciar sesión en OTRO teléfono, el usuario ve todos sus movimientos.
    suspend fun sincronizarHistorial(): Resultado<Int> = withContext(Dispatchers.IO) {
        llamarServidor {
            logHilo("Repository: GET /movimientos (Retrofit)")
            val remotos = api.movimientos()
            val filas = remotos.mapNotNull { dto ->
                // runCatching: si llegara un tipo desconocido, se omite ese movimiento (mapNotNull descarta null).
                val tipo = runCatching { TipoOperacion.valueOf(dto.tipo) }.getOrNull() ?: return@mapNotNull null
                Movimiento(
                    tipo = tipo, monto = dto.monto, fechaHora = dto.fecha, detalle = dto.detalle,
                    categoria = dto.categoria, transaccionId = dto.transaccionId
                ).aEntidad(cuenta)
            }
            // insertarTodos devuelve -1 por cada fila ignorada (ya existía).
            val nuevos = movimientoDao.insertarTodos(filas).count { it != -1L }
            logHilo("Repository: historial sincronizado (${remotos.size} en el servidor, $nuevos nuevos)")
            nuevos
        }
    }

    // Consulta una factura de servicio básico (para mostrar el monto antes de pagar).
    suspend fun consultarFactura(servicio: String, codigo: String): Resultado<Factura> = withContext(Dispatchers.IO) {
        llamarServidor {
            logHilo("Repository: GET /contratos/$codigo?servicio=$servicio (Retrofit)")
            val dto = api.consultarContrato(codigo, servicio)
            Factura(dto.codigo, dto.servicio, dto.titular, dto.monto, dto.pagado)
        }
    }

    // Ejecuta en el servidor cualquiera de las 4 operaciones y guarda el comprobante en Room.
    suspend fun ejecutar(solicitud: Solicitud): Resultado<OperacionRespuestaDto> = withContext(Dispatchers.IO) {
        llamarServidor {
            val respuesta = when (solicitud) {
                is Solicitud.Deposito -> {
                    logHilo("Repository: POST /depositar (Retrofit)")
                    api.depositar(MontoRequest(solicitud.monto))
                }
                is Solicitud.Retiro -> {
                    logHilo("Repository: POST /retirar (Retrofit)")
                    api.retirar(MontoRequest(solicitud.monto))
                }
                is Solicitud.Transferencia -> {
                    logHilo("Repository: POST /transferir a ${solicitud.cuentaDestino} (Retrofit)")
                    api.transferir(TransferenciaRequest(solicitud.cuentaDestino, solicitud.monto))
                }
                is Solicitud.PagoServicio -> {
                    logHilo("Repository: POST /pagar-servicio ${solicitud.servicio} ${solicitud.contrato} (Retrofit)")
                    api.pagarServicio(PagoServicioRequest(solicitud.servicio, solicitud.contrato))
                }
            }
            logHilo("Repository: servidor OK (saldo ${respuesta.saldo}), guardando comprobante en Room")
            movimientoDao.insertar(crearMovimiento(solicitud, respuesta).aEntidad(cuenta))
            guardarSaldo(respuesta.saldo)
            respuesta
        }
    }

    // ---------------------------- PANEL DE PRUEBAS DE CONCURRENCIA ----------------------------
    // Estas funciones existen SOLO para demostrar en qué hilo corre cada operación.
    // Devuelven el nombre del hilo donde se ejecutó el trabajo de red o de base de datos.

    // Una petición de red (GET /mi-cuenta) en Dispatchers.IO. Devuelve el hilo IO que la atendió.
    suspend fun consultaServidorEnHilo(): ResultadoHilo = withContext(Dispatchers.IO) {
        val hiloIO = Thread.currentThread().name                 // Debe ser DefaultDispatcher-worker-N.
        val ok = runCatching { api.miCuenta() }.isSuccess        // La E/S real de OkHttp ocurre aquí.
        ResultadoHilo(ok, hiloIO)
    }

    // Una lectura de Room en Dispatchers.IO. Devuelve el hilo IO que la atendió.
    suspend fun lecturaRoomEnHilo(): ResultadoHilo = withContext(Dispatchers.IO) {
        val hiloIO = Thread.currentThread().name
        runCatching { movimientoDao.contar(cuenta) }
        ResultadoHilo(true, hiloIO)
    }

    // Una TRANSFERENCIA (para la demo de dos pantallas del usuario laboratorio).
    // Se ejecuta en Dispatchers.IO, guarda el comprobante en Room y devuelve el hilo IO que la atendió.
    suspend fun transferenciaEnHilo(destino: String, monto: Double): ResultadoHilo = withContext(Dispatchers.IO) {
        val hiloIO = Thread.currentThread().name
        val ok = runCatching {
            val r = api.transferir(TransferenciaRequest(destino, monto))
            movimientoDao.insertar(crearMovimiento(Solicitud.Transferencia(destino, monto), r).aEntidad(cuenta))
            guardarSaldo(r.saldo)
        }.isSuccess
        ResultadoHilo(ok, hiloIO)
    }

    // ---------------------------- TIEMPO REAL (WebSocket) ----------------------------

    // Mantiene el túnel abierto con reconexión automática y backoff (1, 2, 4, 8, 10 s...).
    // Termina cuando se cancela la corrutina o cuando el servidor RECHAZA la sesión.
    suspend fun mantenerTiempoReal() = withContext(Dispatchers.IO) {
        var intentosFallidos = 0
        while (isActive) {
            // Sin token (se cerró la sesión) no tiene sentido conectar.
            val token = ProveedorToken.token ?: break
            _estadoConexion.value = EstadoConexion.CONECTANDO
            logHilo("Repository: abriendo WebSocket de la cuenta $cuenta (intento ${intentosFallidos + 1})")
            try {
                webSocket.escuchar(ConfigServidor.urlWebSocket(), token).collect { evento ->
                    when (evento) {
                        EventoSocket.Abierto -> {
                            _estadoConexion.value = EstadoConexion.EN_LINEA
                            intentosFallidos = 0
                            // Al (re)conectar descargamos el historial: así aparecen en Room las
                            // transferencias que llegaron mientras el túnel estuvo caído (los repetidos se ignoran).
                            sincronizarHistorial()
                        }
                        is EventoSocket.Mensaje -> procesarMensaje(evento.texto)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SesionRechazadaException) {
                // El servidor dice que la sesión ya no vale: avisamos y NO reintentamos.
                _estadoConexion.value = EstadoConexion.DESCONECTADO
                _sesionRechazada.emit("Tu sesión ya no es válida. Inicia sesión de nuevo")
                break
            } catch (e: Exception) {
                logHilo("Repository: WebSocket caído (${e.javaClass.simpleName})")
            }
            _estadoConexion.value = EstadoConexion.DESCONECTADO
            intentosFallidos++
            val espera = (1000L shl (intentosFallidos - 1).coerceAtMost(4)).coerceAtMost(ESPERA_MAXIMA_MS)
            logHilo("Repository: reintento de WebSocket en ${espera / 1000} s")
            delay(espera)
        }
        _estadoConexion.value = EstadoConexion.DESCONECTADO
    }

    // Interpreta un mensaje JSON del servidor.
    private suspend fun procesarMensaje(texto: String) {
        val evento = runCatching { gson.fromJson(texto, EventoWebSocketDto::class.java) }.getOrNull() ?: return
        when (evento.tipo) {
            "CONECTADO" -> guardarSaldo(evento.saldo)
            "TRANSFERENCIA_RECIBIDA" -> {
                val movimiento = Movimiento(
                    tipo = TipoOperacion.TRANSFERENCIA_RECIBIDA,
                    monto = evento.monto ?: 0.0,
                    fechaHora = evento.fecha,
                    detalle = evento.detalle ?: "De la cuenta ${evento.cuentaOrigen ?: "desconocida"}",
                    transaccionId = evento.transaccionId
                )
                logHilo("Repository: guardando transferencia recibida en Room (saldo nuevo ${evento.saldo})")
                movimientoDao.insertar(movimiento.aEntidad(cuenta))
                guardarSaldo(evento.saldo)
                _transferenciasRecibidas.emit(movimiento)
            }
            else -> logHilo("Repository: mensaje WebSocket ignorado (${evento.tipo})")
        }
    }

    // ---------------------------- AYUDANTES PRIVADOS ----------------------------

    // Comprobante local a partir de lo que pidió el usuario y lo que respondió el servidor.
    private fun crearMovimiento(solicitud: Solicitud, respuesta: OperacionRespuestaDto) = Movimiento(
        tipo = solicitud.tipo,
        monto = respuesta.monto,
        fechaHora = respuesta.fecha,
        detalle = when (solicitud) {
            is Solicitud.Deposito -> "Depósito en cuenta $cuenta"
            is Solicitud.Retiro -> "Retiro de efectivo"
            is Solicitud.Transferencia -> "A la cuenta ${solicitud.cuentaDestino}"
            is Solicitud.PagoServicio -> "${solicitud.servicio} - Contrato ${solicitud.contrato}"
        },
        categoria = if (solicitud is Solicitud.PagoServicio) CATEGORIA_SERVICIO_BASICO else null,
        transaccionId = respuesta.transaccionId
    )

    // Guarda el saldo que dijo el servidor como "último saldo conocido" de esta cuenta.
    private suspend fun guardarSaldo(saldo: Double) {
        cuentaDao.guardar(CuentaLocalEntity(cuenta, saldo, System.currentTimeMillis()))
    }
}
