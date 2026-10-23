// Paquete de comunicación con el servidor.
package com.example.carriel_cuenca_billeteradistribuida.data.remote

import com.example.carriel_cuenca_billeteradistribuida.data.remote.dto.ContratoDto
import com.example.carriel_cuenca_billeteradistribuida.data.remote.dto.CuentaDto
import com.example.carriel_cuenca_billeteradistribuida.data.remote.dto.LoginRequest
import com.example.carriel_cuenca_billeteradistribuida.data.remote.dto.MontoRequest
import com.example.carriel_cuenca_billeteradistribuida.data.remote.dto.MovimientoDto
import com.example.carriel_cuenca_billeteradistribuida.data.remote.dto.OperacionRespuestaDto
import com.example.carriel_cuenca_billeteradistribuida.data.remote.dto.PagoServicioRequest
import com.example.carriel_cuenca_billeteradistribuida.data.remote.dto.RegistroRequest
import com.example.carriel_cuenca_billeteradistribuida.data.remote.dto.SesionDto
import com.example.carriel_cuenca_billeteradistribuida.data.remote.dto.TransferenciaRequest
import retrofit2.Response        // Respuesta "cruda" (para el logout, que no devuelve cuerpo).
import retrofit2.http.Body       // El parámetro se envía como JSON en el cuerpo.
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path       // Reemplaza un {trozo} de la URL.
import retrofit2.http.Query      // Agrega "?nombre=valor" a la URL.

// INTERFAZ DE RETROFIT: los endpoints del servidor como funciones Kotlin "suspend".
// El token NO aparece aquí: lo añade AuthInterceptor a todas las peticiones.
// Si el servidor responde 4xx/5xx, Retrofit lanza HttpException (se atrapa en llamarServidor).
// Con "suspend", Retrofit hace la petición en los hilos de OkHttp y la corrutina espera sin bloquear.
// La ruta de cada anotación (ej. "mi-cuenta") se suma a la URL base de RetrofitClient.
interface BilleteraApi {

    // ---------- Autenticación (públicas) ----------
    @POST("auth/login")
    suspend fun login(@Body cuerpo: LoginRequest): SesionDto

    @POST("auth/registro")
    suspend fun registrar(@Body cuerpo: RegistroRequest): SesionDto

    // Responde 204 (sin cuerpo): por eso devolvemos Response<Unit>.
    @POST("auth/logout")
    suspend fun logout(): Response<Unit>

    // ---------- Protegidas (llevan token) ----------
    @GET("mi-cuenta")
    suspend fun miCuenta(): CuentaDto

    @GET("movimientos")
    suspend fun movimientos(): List<MovimientoDto>

    // GET /contratos/445566?servicio=LUZ
    @GET("contratos/{codigo}")
    suspend fun consultarContrato(@Path("codigo") codigo: String, @Query("servicio") servicio: String): ContratoDto

    @POST("depositar")
    suspend fun depositar(@Body cuerpo: MontoRequest): OperacionRespuestaDto

    @POST("retirar")
    suspend fun retirar(@Body cuerpo: MontoRequest): OperacionRespuestaDto

    @POST("transferir")
    suspend fun transferir(@Body cuerpo: TransferenciaRequest): OperacionRespuestaDto

    @POST("pagar-servicio")
    suspend fun pagarServicio(@Body cuerpo: PagoServicioRequest): OperacionRespuestaDto
}
