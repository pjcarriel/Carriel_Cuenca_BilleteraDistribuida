// Paquete de comunicación con el servidor.
package com.example.carriel_cuenca_billeteradistribuida.data.remote

import com.example.carriel_cuenca_billeteradistribuida.data.remote.dto.CuentaDto
import com.example.carriel_cuenca_billeteradistribuida.data.remote.dto.OperacionRespuestaDto
import com.example.carriel_cuenca_billeteradistribuida.data.remote.dto.OperacionSimpleRequest
import com.example.carriel_cuenca_billeteradistribuida.data.remote.dto.PagoServicioRequest
import com.example.carriel_cuenca_billeteradistribuida.data.remote.dto.TransferenciaRequest
import retrofit2.http.Body      // El parámetro se envía como JSON en el cuerpo de la petición.
import retrofit2.http.GET       // Petición HTTP GET (consultar).
import retrofit2.http.POST      // Petición HTTP POST (crear/ejecutar una operación).
import retrofit2.http.Path      // Reemplaza un {trozo} de la URL por un valor.

// INTERFAZ DE RETROFIT: describimos los endpoints del servidor como funciones Kotlin.
// Retrofit genera el código que arma la petición HTTP, la envía y convierte el JSON de respuesta.
// Son "suspend": la corrutina se pausa mientras espera la red, sin bloquear el hilo.
// Si el servidor responde 4xx o 5xx, Retrofit lanza una HttpException (la atrapamos en el Repository).
interface BilleteraApi {

    // GET http://10.0.2.2:8090/cuentas/1001
    @GET("cuentas/{numero}")
    suspend fun consultarCuenta(@Path("numero") numero: String): CuentaDto

    // POST http://10.0.2.2:8090/depositar   con cuerpo JSON {"cuenta":..., "monto":...}
    @POST("depositar")
    suspend fun depositar(@Body cuerpo: OperacionSimpleRequest): OperacionRespuestaDto

    // POST /retirar  -> puede responder 409 (saldo insuficiente).
    @POST("retirar")
    suspend fun retirar(@Body cuerpo: OperacionSimpleRequest): OperacionRespuestaDto

    // POST /transferir  -> el servidor además avisa al receptor por WebSocket.
    @POST("transferir")
    suspend fun transferir(@Body cuerpo: TransferenciaRequest): OperacionRespuestaDto

    // POST /pagar-servicio
    @POST("pagar-servicio")
    suspend fun pagarServicio(@Body cuerpo: PagoServicioRequest): OperacionRespuestaDto
}
