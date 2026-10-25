// =====================================================================
//   Es el "MENÚ" del servidor: la lista de todo lo que la app le puede
//   pedir (iniciar sesión, ver saldo, depositar, transferir, pagar...).
// =====================================================================

package com.example.carriel_cuenca_billeteradistribuida.data.remote

import com.example.carriel_cuenca_billeteradistribuida.data.remote.dto.ContratoDto
import com.example.carriel_cuenca_billeteradistribuida.data.remote.dto.CuentaDto
import com.example.carriel_cuenca_billeteradistribuida.data.remote.dto.LoginRequest
import com.example.carriel_cuenca_billeteradistribuida.data.remote.dto.MontoRequest
import com.example.carriel_cuenca_billeteradistribuida.data.remote.dto.MovimientoDto
import com.example.carriel_cuenca_billeteradistribuida.data.remote.dto.NuevaFacturaRequest
import com.example.carriel_cuenca_billeteradistribuida.data.remote.dto.NuevoServicioRequest
import com.example.carriel_cuenca_billeteradistribuida.data.remote.dto.ServicioDto
import com.example.carriel_cuenca_billeteradistribuida.data.remote.dto.OperacionRespuestaDto
import com.example.carriel_cuenca_billeteradistribuida.data.remote.dto.PagoServicioRequest
import com.example.carriel_cuenca_billeteradistribuida.data.remote.dto.RegistroRequest
import com.example.carriel_cuenca_billeteradistribuida.data.remote.dto.SesionDto
import com.example.carriel_cuenca_billeteradistribuida.data.remote.dto.TransferenciaRequest
import retrofit2.Response        // Respuesta completa (para el logout, que no devuelve datos).
import retrofit2.http.Body       // Los datos se envían como JSON.
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path       // Pone un valor dentro de la dirección.
import retrofit2.http.Query      // Agrega "?nombre=valor" a la dirección.

// Pedidos al servidor: @GET = pedir datos, @POST = enviar datos.
// El token lo agrega AuthInterceptor; "suspend" = espera sin congelar la pantalla.
interface BilleteraApi {

    // ---------- Inicio de sesión (sin token) ----------

    // Entrar con usuario y contraseña; devuelve el token y los datos de la cuenta.
    @POST("auth/login")
    suspend fun login(@Body cuerpo: LoginRequest): SesionDto

    // Crear una cuenta nueva.
    @POST("auth/registro")
    suspend fun registrar(@Body cuerpo: RegistroRequest): SesionDto

    // Cerrar sesión (el servidor no devuelve datos).
    @POST("auth/logout")
    suspend fun logout(): Response<Unit>

    // ---------- Con sesión iniciada (llevan token) ----------

    // Mi cuenta: número, titular y saldo.
    @GET("mi-cuenta")
    suspend fun miCuenta(): CuentaDto

    // Mi historial de movimientos.
    @GET("movimientos")
    suspend fun movimientos(): List<MovimientoDto>

    // Buscar una factura por contrato. Ej: /contratos/445566?servicio=LUZ
    @GET("contratos/{codigo}")
    suspend fun consultarContrato(@Path("codigo") codigo: String, @Query("servicio") servicio: String): ContratoDto

    // Buscar las facturas de una cédula. Ej: /facturas?cedula=1712345675&servicio=LUZ
    @GET("facturas")
    suspend fun facturasPorCedula(@Query("cedula") cedula: String, @Query("servicio") servicio: String): List<ContratoDto>

    // ---------- Solo laboratorio (otra cuenta recibe error 403) ----------

    // Todas las facturas, con filtros opcionales por estado y servicio.
    @GET("admin/facturas")
    suspend fun adminFacturas(@Query("estado") estado: String?, @Query("servicio") servicio: String?): List<ContratoDto>

    // Emitir una factura nueva.
    @POST("admin/facturas")
    suspend fun crearFactura(@Body cuerpo: NuevaFacturaRequest): ContratoDto

    // ---------- Catálogo de servicios ----------

    // Servicios que se pueden pagar (luz, agua...).
    @GET("servicios")
    suspend fun servicios(): List<ServicioDto>

    // Agregar un servicio al catálogo (solo laboratorio).
    @POST("admin/servicios")
    suspend fun crearServicio(@Body cuerpo: NuevoServicioRequest): ServicioDto

    // ---------- Operaciones con dinero ----------

    // Depositar dinero en mi cuenta.
    @POST("depositar")
    suspend fun depositar(@Body cuerpo: MontoRequest): OperacionRespuestaDto

    // Retirar dinero de mi cuenta.
    @POST("retirar")
    suspend fun retirar(@Body cuerpo: MontoRequest): OperacionRespuestaDto

    // Enviar dinero a otra cuenta.
    @POST("transferir")
    suspend fun transferir(@Body cuerpo: TransferenciaRequest): OperacionRespuestaDto

    // Pagar la factura de un servicio.
    @POST("pagar-servicio")
    suspend fun pagarServicio(@Body cuerpo: PagoServicioRequest): OperacionRespuestaDto
}
