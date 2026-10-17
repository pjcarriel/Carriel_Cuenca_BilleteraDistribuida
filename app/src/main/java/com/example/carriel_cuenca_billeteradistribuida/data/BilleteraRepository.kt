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
import com.example.carriel_cuenca_billeteradistribuida.data.remote.WebSocketCliente
import com.example.carriel_cuenca_billeteradistribuida.data.remote.dto.ErrorRespuestaDto
import com.example.carriel_cuenca_billeteradistribuida.data.remote.dto.EventoWebSocketDto
import com.example.carriel_cuenca_billeteradistribuida.data.remote.dto.OperacionRespuestaDto
import com.example.carriel_cuenca_billeteradistribuida.data.remote.dto.OperacionSimpleRequest
import com.example.carriel_cuenca_billeteradistribuida.data.remote.dto.PagoServicioRequest
import com.example.carriel_cuenca_billeteradistribuida.data.remote.dto.TransferenciaRequest
import com.example.carriel_cuenca_billeteradistribuida.model.CATEGORIA_SERVICIO_BASICO
import com.example.carriel_cuenca_billeteradistribuida.model.EstadoConexion
import com.example.carriel_cuenca_billeteradistribuida.model.Movimiento
import com.example.carriel_cuenca_billeteradistribuida.model.Solicitud
import com.example.carriel_cuenca_billeteradistribuida.model.TipoOperacion
import com.example.carriel_cuenca_billeteradistribuida.util.logHilo
import com.google.gson.Gson                    // Para leer JSON (errores y mensajes del WebSocket).
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers          // Dispatchers.IO = hilos para disco y red.
import kotlinx.coroutines.delay                // Espera SIN bloquear el hilo.
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive             // ¿La corrutina sigue viva (no cancelada)?
import kotlinx.coroutines.withContext
import retrofit2.HttpException                 // Error HTTP (400, 404, 409, 500...).
import java.io.IOException                     // Error de red (servidor apagado, sin internet).

// Tiempo máximo de espera entre reintentos de conexión del WebSocket (10 segundos).
private const val ESPERA_MAXIMA_MS = 10_000L

// REPOSITORIO: el ÚNICO lugar de la app que sabe DE DÓNDE vienen los datos.
// Combina tres fuentes:
//   - Room (local): historial de comprobantes y último saldo conocido.
//   - Retrofit (REST): operaciones que ENVÍA el usuario.
//   - WebSocket: transferencias que RECIBE el usuario en tiempo real.
// REGLA DE ESTE ARCHIVO: todo trabajo de disco/red se hace en Dispatchers.IO.
class BilleteraRepository(
    private val movimientoDao: MovimientoDao,        // Tabla de movimientos (Room).
    private val cuentaDao: CuentaDao,                // Tabla del último saldo (Room).
    private val api: BilleteraApi,                   // Servidor REST (Retrofit).
    private val webSocket: WebSocketCliente,         // Servidor en tiempo real (WebSocket).
    private val cuenta: String = ConfigServidor.CUENTA_APP   // Cuenta de esta app ("1001").
) {

    private val gson = Gson()

    // Estado del túnel WebSocket (lo observa el indicador verde/rojo de la pantalla).
    private val _estadoConexion = MutableStateFlow(EstadoConexion.DESCONECTADO)
    val estadoConexion: StateFlow<EstadoConexion> = _estadoConexion.asStateFlow()

    // Aviso de cada transferencia recibida (para mostrar "Recibiste $..." en pantalla).
    // SharedFlow = flujo de EVENTOS (a diferencia de StateFlow, no guarda un "valor actual").
    // extraBufferCapacity: si llegan varias seguidas, se guardan hasta 10 sin perderse.
    private val _transferenciasRecibidas = MutableSharedFlow<Movimiento>(extraBufferCapacity = 10)
    val transferenciasRecibidas: SharedFlow<Movimiento> = _transferenciasRecibidas.asSharedFlow()

    // ---------------------------- LECTURAS (Flow) ----------------------------

    // Historial: viene SIEMPRE de Room (funciona sin internet).
    fun observarHistorial(): Flow<List<Movimiento>> =
        movimientoDao.observarTodos()
            .map { entidades ->
                logHilo("Repository: leyendo historial de Room (${entidades.size} filas)")
                entidades.map { it.aModelo() }
            }
            .flowOn(Dispatchers.IO)                  // Consulta y conversión en hilo IO.

    // Saldo: también viene de Room; REST y WebSocket lo actualizan ahí y este Flow avisa a la UI.
    fun observarSaldo(): Flow<Double?> =
        cuentaDao.observarSaldo(cuenta).flowOn(Dispatchers.IO)

    // ---------------------------- TIEMPO REAL (WebSocket) ----------------------------

    // Mantiene el túnel WebSocket abierto MIENTRAS la corrutina que lo llama siga viva.
    // Si el túnel se cae, espera un poco y lo vuelve a abrir (RECONEXIÓN AUTOMÁTICA).
    // La espera crece en cada intento fallido: 1 s, 2 s, 4 s, 8 s, 10 s, 10 s... ("backoff exponencial"),
    // para no saturar a un servidor que está caído.
    suspend fun mantenerTiempoReal() = withContext(Dispatchers.IO) {
        var intentosFallidos = 0
        while (isActive) {                           // Hasta que el ViewModel se destruya.
            _estadoConexion.value = EstadoConexion.CONECTANDO
            logHilo("Repository: abriendo WebSocket (intento ${intentosFallidos + 1})")
            try {
                // collect se queda aquí ESCUCHANDO mientras el túnel siga abierto.
                webSocket.escuchar(ConfigServidor.URL_WEBSOCKET + cuenta).collect { evento ->
                    when (evento) {
                        EventoSocket.Abierto -> {
                            _estadoConexion.value = EstadoConexion.EN_LINEA
                            intentosFallidos = 0     // Conectó: reiniciamos la espera.
                        }
                        is EventoSocket.Mensaje -> procesarMensaje(evento.texto)
                    }
                }
            } catch (e: CancellationException) {
                throw e                              // Cancelación: salimos del bucle sin reintentar.
            } catch (e: Exception) {
                logHilo("Repository: WebSocket caído (${e.javaClass.simpleName})")
            }
            // Si llegamos aquí, el túnel se cerró o falló.
            _estadoConexion.value = EstadoConexion.DESCONECTADO
            intentosFallidos++
            // "shl" = desplazamiento de bits: 1000 shl 2 = 4000. coerceAtMost pone el tope de 10 s.
            val espera = (1000L shl (intentosFallidos - 1).coerceAtMost(4)).coerceAtMost(ESPERA_MAXIMA_MS)
            logHilo("Repository: reintento de WebSocket en ${espera / 1000} s")
            delay(espera)                            // Espera SIN bloquear el hilo.
        }
    }

    // Interpreta un mensaje JSON del servidor.
    private suspend fun procesarMensaje(texto: String) {
        // Si el JSON viene mal formado, lo ignoramos (getOrNull devuelve null y "?: return" sale).
        val evento = runCatching { gson.fromJson(texto, EventoWebSocketDto::class.java) }.getOrNull() ?: return
        when (evento.tipo) {
            // Al conectarse, el servidor manda el saldo actual: sincronizamos sin pedirlo por REST.
            "CONECTADO" -> guardarSaldo(evento.saldo)

            // ¡Alguien nos envió dinero! Guardamos el comprobante y el saldo nuevo en Room.
            // La pantalla se actualiza SOLA porque observa Room con Flow.
            "TRANSFERENCIA_RECIBIDA" -> {
                val movimiento = Movimiento(
                    tipo = TipoOperacion.TRANSFERENCIA_RECIBIDA,
                    monto = evento.monto ?: 0.0,     // "?:" valor alterno si viniera null.
                    fechaHora = evento.fecha,
                    detalle = "De la cuenta ${evento.cuentaOrigen ?: "desconocida"}"
                )
                logHilo("Repository: guardando transferencia recibida en Room (saldo nuevo ${evento.saldo})")
                movimientoDao.insertar(movimiento.aEntidad())
                guardarSaldo(evento.saldo)
                _transferenciasRecibidas.emit(movimiento)    // Avisamos al ViewModel.
            }
            else -> logHilo("Repository: mensaje WebSocket ignorado (${evento.tipo})")
        }
    }

    // ---------------------------- SERVIDOR (REST) ----------------------------

    // Pide el saldo actual al servidor y lo guarda en Room.
    suspend fun sincronizarSaldo(): Resultado<Double> = withContext(Dispatchers.IO) {
        llamarServidor {
            logHilo("Repository: GET /cuentas/$cuenta (Retrofit)")
            val datos = api.consultarCuenta(cuenta)          // Llamada de red (suspend).
            guardarSaldo(datos.saldo)                        // Copia local en Room.
            datos.saldo
        }
    }

    // Ejecuta en el servidor cualquiera de las 4 operaciones.
    // Si sale bien: guarda el comprobante y el saldo nuevo en Room.
    // Si sale mal: devuelve Resultado.Error con un mensaje claro (NO lanza la excepción).
    suspend fun ejecutar(solicitud: Solicitud): Resultado<OperacionRespuestaDto> = withContext(Dispatchers.IO) {
        llamarServidor {
            // "when" sobre una sealed interface: Kotlin obliga a cubrir las 4 solicitudes.
            val respuesta = when (solicitud) {
                is Solicitud.Deposito -> {
                    logHilo("Repository: POST /depositar (Retrofit)")
                    api.depositar(OperacionSimpleRequest(cuenta, solicitud.monto))
                }
                is Solicitud.Retiro -> {
                    logHilo("Repository: POST /retirar (Retrofit)")
                    api.retirar(OperacionSimpleRequest(cuenta, solicitud.monto))
                }
                is Solicitud.Transferencia -> {
                    logHilo("Repository: POST /transferir a ${solicitud.cuentaDestino} (Retrofit)")
                    api.transferir(TransferenciaRequest(cuenta, solicitud.cuentaDestino, solicitud.monto))
                }
                is Solicitud.PagoServicio -> {
                    logHilo("Repository: POST /pagar-servicio ${solicitud.servicio} (Retrofit)")
                    api.pagarServicio(PagoServicioRequest(cuenta, solicitud.servicio, solicitud.contrato, solicitud.monto))
                }
            }
            // Si llegamos aquí, el servidor respondió 200. Guardamos el comprobante en Room.
            logHilo("Repository: servidor OK (saldo ${respuesta.saldo}), guardando comprobante en Room")
            movimientoDao.insertar(crearMovimiento(solicitud, respuesta).aEntidad())
            guardarSaldo(respuesta.saldo)
            respuesta
        }
    }

    // ---------------------------- AYUDANTES PRIVADOS ----------------------------

    // Ejecuta un bloque que habla con el servidor y convierte CUALQUIER fallo en Resultado.Error.
    private suspend fun <T> llamarServidor(bloque: suspend () -> T): Resultado<T> =
        try {
            Resultado.Exito(bloque())
        } catch (e: CancellationException) {
            throw e                                          // Cancelar no es un error: se deja pasar.
        } catch (e: HttpException) {
            // El servidor SÍ respondió, pero con un código de error (400, 404, 409...).
            val mensaje = leerMensajeDeError(e)
            logHilo("Repository: error HTTP ${e.code()} -> $mensaje")
            Resultado.Error(mensaje, e.code())
        } catch (e: IOException) {
            // No hubo respuesta: servidor apagado, sin red o tiempo agotado.
            logHilo("Repository: sin conexión (${e.javaClass.simpleName})")
            Resultado.Error("Sin conexión con el servidor. Se muestran los últimos datos guardados.")
        }

    // Lee el JSON de error del servidor ({"codigo":..., "mensaje":...}) para mostrar su mensaje.
    private fun leerMensajeDeError(e: HttpException): String {
        val cuerpo = e.response()?.errorBody()?.string()
        val error = runCatching { gson.fromJson(cuerpo, ErrorRespuestaDto::class.java) }.getOrNull()
        return error?.mensaje ?: when (e.code()) {
            409 -> "Saldo insuficiente"
            404 -> "La cuenta no existe"
            400 -> "Datos inválidos"
            else -> "Error del servidor (${e.code()})"
        }
    }

    // Arma el comprobante local a partir de lo que pidió el usuario y lo que respondió el servidor.
    private fun crearMovimiento(solicitud: Solicitud, respuesta: OperacionRespuestaDto) = Movimiento(
        tipo = solicitud.tipo,
        monto = respuesta.monto,
        fechaHora = respuesta.fecha,                         // Usamos la fecha del SERVIDOR.
        detalle = when (solicitud) {
            is Solicitud.Deposito -> "Depósito en cuenta $cuenta"
            is Solicitud.Retiro -> "Retiro de efectivo"
            is Solicitud.Transferencia -> "A la cuenta ${solicitud.cuentaDestino}"
            is Solicitud.PagoServicio -> "${solicitud.servicio} - Contrato ${solicitud.contrato}"
        },
        categoria = if (solicitud is Solicitud.PagoServicio) CATEGORIA_SERVICIO_BASICO else null
    )

    // Guarda el saldo que dijo el servidor como "último saldo conocido".
    private suspend fun guardarSaldo(saldo: Double) {
        cuentaDao.guardar(CuentaLocalEntity(cuenta, saldo, System.currentTimeMillis()))
    }
}
