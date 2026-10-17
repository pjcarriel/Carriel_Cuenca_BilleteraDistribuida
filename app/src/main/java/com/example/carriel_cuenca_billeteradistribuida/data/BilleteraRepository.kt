// Paquete "data": todo lo relacionado con OBTENER y GUARDAR datos.
package com.example.carriel_cuenca_billeteradistribuida.data

import com.example.carriel_cuenca_billeteradistribuida.data.local.CuentaDao
import com.example.carriel_cuenca_billeteradistribuida.data.local.CuentaLocalEntity
import com.example.carriel_cuenca_billeteradistribuida.data.local.MovimientoDao
import com.example.carriel_cuenca_billeteradistribuida.data.local.aEntidad
import com.example.carriel_cuenca_billeteradistribuida.data.local.aModelo
import com.example.carriel_cuenca_billeteradistribuida.data.remote.BilleteraApi
import com.example.carriel_cuenca_billeteradistribuida.data.remote.ConfigServidor
import com.example.carriel_cuenca_billeteradistribuida.data.remote.dto.ErrorRespuestaDto
import com.example.carriel_cuenca_billeteradistribuida.data.remote.dto.OperacionRespuestaDto
import com.example.carriel_cuenca_billeteradistribuida.data.remote.dto.OperacionSimpleRequest
import com.example.carriel_cuenca_billeteradistribuida.data.remote.dto.PagoServicioRequest
import com.example.carriel_cuenca_billeteradistribuida.data.remote.dto.TransferenciaRequest
import com.example.carriel_cuenca_billeteradistribuida.model.CATEGORIA_SERVICIO_BASICO
import com.example.carriel_cuenca_billeteradistribuida.model.Movimiento
import com.example.carriel_cuenca_billeteradistribuida.model.Solicitud
import com.example.carriel_cuenca_billeteradistribuida.util.logHilo
import com.google.gson.Gson                    // Para leer el JSON de las respuestas de error.
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers          // Dispatchers.IO = hilos para disco y red.
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import retrofit2.HttpException                 // Error HTTP (400, 404, 409, 500...).
import java.io.IOException                     // Error de red (servidor apagado, sin internet).

// REPOSITORIO: el ÚNICO lugar de la app que sabe DE DÓNDE vienen los datos.
// Desde la Fase 4 combina dos fuentes:
//   - Room (local): historial de comprobantes y último saldo conocido.
//   - Retrofit (remoto): el servidor, que es la "verdad" del saldo.
// REGLA DE ESTE ARCHIVO: todo trabajo de disco/red se hace en Dispatchers.IO.
class BilleteraRepository(
    private val movimientoDao: MovimientoDao,        // Tabla de movimientos (Room).
    private val cuentaDao: CuentaDao,                // Tabla del último saldo (Room).
    private val api: BilleteraApi,                   // Servidor (Retrofit).
    private val cuenta: String = ConfigServidor.CUENTA_APP   // Cuenta de esta app ("1001").
) {

    // ---------------------------- LECTURAS (Flow) ----------------------------

    // Historial: viene SIEMPRE de Room (funciona sin internet).
    fun observarHistorial(): Flow<List<Movimiento>> =
        movimientoDao.observarTodos()
            .map { entidades ->
                logHilo("Repository: leyendo historial de Room (${entidades.size} filas)")
                entidades.map { it.aModelo() }
            }
            .flowOn(Dispatchers.IO)                  // Consulta y conversión en hilo IO.

    // Saldo: también viene de Room. Cuando el servidor responde, lo guardamos en Room
    // y este Flow avisa a la pantalla. Así hay UNA sola fuente para la UI.
    fun observarSaldo(): Flow<Double?> =
        cuentaDao.observarSaldo(cuenta).flowOn(Dispatchers.IO)

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
            // Dentro de cada rama, "solicitud" ya es del tipo exacto (smart cast) y podemos leer sus campos.
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
    // "<T>" genérico: sirve tanto para el saldo (Double) como para una operación.
    // "suspend () -> T" = lambda suspendible (puede llamar a Retrofit dentro).
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
        val cuerpo = e.response()?.errorBody()?.string()     // "?." en cadena: si algo es null, todo es null.
        // runCatching: si el cuerpo no es un JSON válido, getOrNull() devuelve null en vez de fallar.
        val error = runCatching { Gson().fromJson(cuerpo, ErrorRespuestaDto::class.java) }.getOrNull()
        // Si el servidor mandó un mensaje lo usamos; si no, un texto según el código HTTP.
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
        // Solo los pagos de servicio llevan la categoría "Servicio Básico".
        categoria = if (solicitud is Solicitud.PagoServicio) CATEGORIA_SERVICIO_BASICO else null
    )

    // Guarda el saldo que dijo el servidor como "último saldo conocido".
    private suspend fun guardarSaldo(saldo: Double) {
        cuentaDao.guardar(CuentaLocalEntity(cuenta, saldo, System.currentTimeMillis()))
    }
}
