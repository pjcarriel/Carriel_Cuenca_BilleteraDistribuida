// =====================================================================
//   Es el "cajero" de la billetera: lleva cada operación al servidor,
//   guarda el comprobante y el saldo en el teléfono y recibe los avisos
//   en tiempo real.
// =====================================================================

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
import com.example.carriel_cuenca_billeteradistribuida.data.remote.dto.ContratoDto
import com.example.carriel_cuenca_billeteradistribuida.data.remote.dto.EventoAvisoDto
import com.example.carriel_cuenca_billeteradistribuida.data.remote.dto.EventoConexionesDto
import com.example.carriel_cuenca_billeteradistribuida.data.remote.dto.NuevaFacturaRequest
import com.example.carriel_cuenca_billeteradistribuida.data.remote.dto.NuevoServicioRequest
import com.example.carriel_cuenca_billeteradistribuida.model.Servicio
import com.example.carriel_cuenca_billeteradistribuida.data.remote.dto.EventoWebSocketDto
import com.example.carriel_cuenca_billeteradistribuida.data.remote.dto.MontoRequest
import com.example.carriel_cuenca_billeteradistribuida.data.remote.dto.OperacionRespuestaDto
import com.example.carriel_cuenca_billeteradistribuida.data.remote.dto.PagoServicioRequest
import com.example.carriel_cuenca_billeteradistribuida.data.remote.dto.TransferenciaRequest
import com.example.carriel_cuenca_billeteradistribuida.model.CATEGORIA_SERVICIO_BASICO
import com.example.carriel_cuenca_billeteradistribuida.model.EstadoConexion
import com.example.carriel_cuenca_billeteradistribuida.model.Factura
import com.example.carriel_cuenca_billeteradistribuida.model.Movimiento
import com.example.carriel_cuenca_billeteradistribuida.model.NodosConectados
import com.example.carriel_cuenca_billeteradistribuida.model.Solicitud
import com.example.carriel_cuenca_billeteradistribuida.model.TipoOperacion
import com.example.carriel_cuenca_billeteradistribuida.util.logHilo
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers          // Dispatchers.IO = hilos de fondo para internet y disco.
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

// Lo máximo que se espera entre reintentos de conexión (10 segundos).
private const val ESPERA_MAXIMA_MS = 10_000L

// Resultado de una prueba del panel: si salió bien y en qué hilo corrió.
data class ResultadoHilo(val exito: Boolean, val hilo: String)

// Resultado de una operación exitosa: mensaje del servidor y comprobante guardado.
data class OperacionHecha(val mensaje: String, val movimiento: Movimiento)

// Repositorio de la billetera del usuario que inició sesión.
// Todo lo de internet o disco va en segundo plano (Dispatchers.IO).
class BilleteraRepository(
    private val movimientoDao: MovimientoDao,
    private val cuentaDao: CuentaDao,
    private val webSocket: WebSocketCliente,
    private val cuenta: String                       // Cuenta de la sesión (ej. "1001", "1004"...).
) {
    // Conexión con el servidor (se pide cada vez, por si cambió la dirección).
    private val api: BilleteraApi get() = RetrofitClient.api

    // Convierte el texto JSON del servidor en objetos.
    private val gson = Gson()

    // Los valores con "_" solo se cambian aquí; afuera se ven como "solo lectura".

    // Estado de la conexión en tiempo real (puntito verde / ámbar / rojo).
    private val _estadoConexion = MutableStateFlow(EstadoConexion.DESCONECTADO)
    val estadoConexion: StateFlow<EstadoConexion> = _estadoConexion.asStateFlow()

    // Teléfonos conectados al servidor (solo laboratorio). null = no se sabe.
    private val _nodosConectados = MutableStateFlow<NodosConectados?>(null)
    val nodosConectados: StateFlow<NodosConectados?> = _nodosConectados.asStateFlow()

    // Aviso (solo laboratorio): cambió alguna factura.
    private val _facturasActualizadas = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val facturasActualizadas: SharedFlow<String> = _facturasActualizadas.asSharedFlow()

    // Aviso: llegó una transferencia (para mostrar "Recibiste $...").
    private val _transferenciasRecibidas = MutableSharedFlow<Movimiento>(extraBufferCapacity = 10)
    val transferenciasRecibidas: SharedFlow<Movimiento> = _transferenciasRecibidas.asSharedFlow()

    // Aviso: el servidor ya no acepta la sesión.
    private val _sesionRechazada = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val sesionRechazada: SharedFlow<String> = _sesionRechazada.asSharedFlow()

    // ---------------------------- LECTURAS (desde el teléfono) ----------------------------

    // Historial de esta cuenta. Funciona sin internet y se actualiza solo.
    fun observarHistorial(): Flow<List<Movimiento>> =
        movimientoDao.observarTodos(cuenta)
            .map { entidades ->
                logHilo("Repository: leyendo historial de Room de la cuenta $cuenta (${entidades.size} filas)")
                entidades.map { it.aModelo() }
            }
            .flowOn(Dispatchers.IO)

    // Último saldo conocido. Cambia solo después de cada operación.
    fun observarSaldo(): Flow<Double?> =
        cuentaDao.observarSaldo(cuenta).flowOn(Dispatchers.IO)

    // ---------------------------- SERVIDOR ----------------------------

    // Pide el saldo actual al servidor y lo guarda en el teléfono.
    suspend fun sincronizarSaldo(): Resultado<Double> = withContext(Dispatchers.IO) {
        llamarServidor {
            logHilo("Repository: GET /mi-cuenta (Retrofit)")
            val datos = api.miCuenta()
            guardarSaldo(datos.saldo)
            datos.saldo
        }
    }

    // Baja el historial del servidor y lo guarda sin repetir (sirve al entrar desde otro teléfono).
    suspend fun sincronizarHistorial(): Resultado<Int> = withContext(Dispatchers.IO) {
        llamarServidor {
            logHilo("Repository: GET /movimientos (Retrofit)")
            val remotos = api.movimientos()
            val filas = remotos.mapNotNull { dto ->
                // Si llega un tipo desconocido, ese movimiento se salta.
                val tipo = runCatching { TipoOperacion.valueOf(dto.tipo) }.getOrNull() ?: return@mapNotNull null
                Movimiento(
                    tipo = tipo, monto = dto.monto, fechaHora = dto.fecha, detalle = dto.detalle,
                    categoria = dto.categoria, transaccionId = dto.transaccionId
                ).aEntidad(cuenta)
            }
            // Cuenta solo los nuevos (los repetidos vuelven como -1).
            val nuevos = movimientoDao.insertarTodos(filas).count { it != -1L }
            logHilo("Repository: historial sincronizado (${remotos.size} en el servidor, $nuevos nuevos)")
            nuevos
        }
    }

    // Busca una factura por número de contrato para ver cuánto se debe antes de pagar.
    suspend fun consultarFactura(servicio: String, codigo: String): Resultado<Factura> = withContext(Dispatchers.IO) {
        llamarServidor {
            logHilo("Repository: GET /contratos/$codigo?servicio=$servicio (Retrofit)")
            api.consultarContrato(codigo, servicio).aFactura()
        }
    }

    // Busca las facturas de una cédula para un servicio (primero las pendientes).
    suspend fun buscarFacturas(cedula: String, servicio: String): Resultado<List<Factura>> = withContext(Dispatchers.IO) {
        llamarServidor {
            logHilo("Repository: GET /facturas?cedula=$cedula&servicio=$servicio (Retrofit)")
            api.facturasPorCedula(cedula, servicio).map { it.aFactura() }
        }
    }

    // ---------------------------- ADMINISTRACIÓN DE SERVICIOS (laboratorio) ----------------------------

    // Lista de facturas filtrada por estado y servicio (null = todas).
    suspend fun adminFacturas(estado: String?, servicio: String?): Resultado<List<Factura>> = withContext(Dispatchers.IO) {
        llamarServidor {
            logHilo("Repository: GET /admin/facturas estado=$estado servicio=$servicio (Retrofit)")
            api.adminFacturas(estado, servicio).map { it.aFactura() }
        }
    }

    // Emite una factura nueva pendiente de pago.
    suspend fun crearFactura(cedula: String, titular: String, servicio: String, periodo: String, monto: Double): Resultado<Factura> =
        withContext(Dispatchers.IO) {
            llamarServidor {
                logHilo("Repository: POST /admin/facturas $servicio $cedula (Retrofit)")
                api.crearFactura(NuevaFacturaRequest(cedula, titular, servicio, periodo, monto)).aFactura()
            }
        }

    // Lista de servicios que se pueden pagar (LUZ, AGUA, INTERNET...).
    suspend fun servicios(): Resultado<List<Servicio>> = withContext(Dispatchers.IO) {
        llamarServidor {
            logHilo("Repository: GET /servicios (Retrofit)")
            api.servicios().map { Servicio(it.codigo, it.nombre, it.icono) }
        }
    }

    // Agrega un servicio nuevo a la lista.
    suspend fun crearServicio(codigo: String, nombre: String, icono: String): Resultado<Servicio> = withContext(Dispatchers.IO) {
        llamarServidor {
            logHilo("Repository: POST /admin/servicios $codigo (Retrofit)")
            api.crearServicio(NuevoServicioRequest(codigo, nombre, icono)).let { Servicio(it.codigo, it.nombre, it.icono) }
        }
    }

    // Convierte la factura que llega del servidor en una Factura de la app.
    private fun ContratoDto.aFactura() =
        Factura(codigo, servicio, titular, monto, pagado, cedula, periodo, pagadoPor, fechaPago)

    // Hace un depósito, retiro, transferencia o pago en el servidor.
    // Si sale bien, guarda el comprobante y el saldo; si falla, no se guarda nada.
    suspend fun ejecutar(solicitud: Solicitud): Resultado<OperacionHecha> = withContext(Dispatchers.IO) {
        llamarServidor {
            // Según el tipo de pedido, se llama a la dirección correcta del servidor.
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
            val movimiento = crearMovimiento(solicitud, respuesta)
            // Se guarda y se le pone al comprobante el número que le dio la base de datos.
            val id = movimientoDao.insertar(movimiento.aEntidad(cuenta))
            guardarSaldo(respuesta.saldo)
            OperacionHecha(respuesta.mensaje, movimiento.copy(id = maxOf(id, 0)))
        }
    }

    // ---------------------------- PANEL DE PRUEBAS DE CONCURRENCIA ----------------------------
    // Solo sirven para demostrar en qué hilo corre cada trabajo.

    // Consulta al servidor y dice qué hilo la atendió.
    suspend fun consultaServidorEnHilo(): ResultadoHilo = withContext(Dispatchers.IO) {
        val hiloIO = Thread.currentThread().name                 // Debería ser "DefaultDispatcher-worker-N".
        val ok = runCatching { api.miCuenta() }.isSuccess
        ResultadoHilo(ok, hiloIO)
    }

    // Lee la base de datos del teléfono y dice qué hilo la atendió.
    suspend fun lecturaRoomEnHilo(): ResultadoHilo = withContext(Dispatchers.IO) {
        val hiloIO = Thread.currentThread().name
        runCatching { movimientoDao.contar(cuenta) }
        ResultadoHilo(true, hiloIO)
    }

    // Hace una transferencia (demo de dos pantallas) y dice qué hilo la atendió.
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

    // Mantiene abierta la conexión en tiempo real; si se cae, reintenta solo.
    // Se detiene al cerrar sesión o si el servidor rechaza la sesión.
    suspend fun mantenerTiempoReal() = withContext(Dispatchers.IO) {
        var intentosFallidos = 0
        while (isActive) {
            // Sin token (sesión cerrada) no se conecta.
            val token = ProveedorToken.token ?: break
            _estadoConexion.value = EstadoConexion.CONECTANDO
            logHilo("Repository: abriendo WebSocket de la cuenta $cuenta (intento ${intentosFallidos + 1})")
            try {
                // Escucha los avisos mientras la conexión esté abierta.
                webSocket.escuchar(ConfigServidor.urlWebSocket(), token).collect { evento ->
                    when (evento) {
                        EventoSocket.Abierto -> {
                            _estadoConexion.value = EstadoConexion.EN_LINEA
                            intentosFallidos = 0
                            // Trae lo que llegó mientras no había conexión.
                            sincronizarHistorial()
                        }
                        is EventoSocket.Mensaje -> procesarMensaje(evento.texto)
                    }
                }
            } catch (e: CancellationException) {
                // Si nos piden parar, se deja pasar para que realmente pare.
                throw e
            } catch (e: SesionRechazadaException) {
                // La sesión ya no vale: se avisa y no se reintenta.
                _estadoConexion.value = EstadoConexion.DESCONECTADO
                _sesionRechazada.emit("Tu sesión ya no es válida. Inicia sesión de nuevo")
                break
            } catch (e: Exception) {
                logHilo("Repository: WebSocket caído (${e.javaClass.simpleName})")
            }
            _estadoConexion.value = EstadoConexion.DESCONECTADO
            _nodosConectados.value = null              // Sin conexión no sabemos quién está conectado.
            intentosFallidos++
            // Espera 1, 2, 4, 8... segundos (máximo 10) antes de reintentar.
            val espera = (1000L shl (intentosFallidos - 1).coerceAtMost(4)).coerceAtMost(ESPERA_MAXIMA_MS)
            logHilo("Repository: reintento de WebSocket en ${espera / 1000} s")
            delay(espera)
        }
        _estadoConexion.value = EstadoConexion.DESCONECTADO
    }

    // Lee un aviso del servidor y decide qué hacer (si viene dañado, se ignora).
    private suspend fun procesarMensaje(texto: String) {
        val evento = runCatching { gson.fromJson(texto, EventoWebSocketDto::class.java) }.getOrNull() ?: return
        when (evento.tipo) {
            // Al conectarse llega el saldo actual.
            "CONECTADO" -> guardarSaldo(evento.saldo)
            // Solo laboratorio: cambió una factura -> se recarga la lista.
            "FACTURAS_ACTUALIZADAS" -> {
                val detalle = runCatching { gson.fromJson(texto, EventoAvisoDto::class.java) }.getOrNull()?.detalle
                logHilo("Repository: facturas actualizadas ($detalle)")
                _facturasActualizadas.emit(detalle ?: "Facturas actualizadas")
            }
            // Solo laboratorio: cuántos teléfonos están conectados.
            "CONEXIONES" -> {
                val dto = runCatching { gson.fromJson(texto, EventoConexionesDto::class.java) }.getOrNull() ?: return
                logHilo("Repository: nodos conectados = ${dto.total} ${dto.cuentas}")
                _nodosConectados.value = NodosConectados(dto.total, dto.cuentas.orEmpty())
            }
            // Nos transfirieron dinero: se guarda, se actualiza el saldo y se avisa a la pantalla.
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

    // Arma el comprobante con lo que pidió el usuario y lo que respondió el servidor.
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

    // Guarda en el teléfono el saldo que dijo el servidor.
    private suspend fun guardarSaldo(saldo: Double) {
        cuentaDao.guardar(CuentaLocalEntity(cuenta, saldo, System.currentTimeMillis()))
    }
}
