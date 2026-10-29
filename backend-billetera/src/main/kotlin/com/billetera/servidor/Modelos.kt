// =====================================================================
//   Define la FORMA de los datos que viajan entre la app y el servidor
//   (lo que la app pide y lo que el servidor responde) y los errores.
// =====================================================================

package com.billetera.servidor

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable

// ======================== REGISTRO E INICIO DE SESIÓN ========================

// Datos para crear una cuenta: usuario, nombre y contraseña.
@Serializable
data class RegistroRequest(val usuario: String, val nombre: String, val password: String)

// Datos para iniciar sesión: usuario y contraseña.
@Serializable
data class LoginRequest(val usuario: String, val password: String)

// Respuesta al entrar: el token (pase) y los datos de la cuenta.
@Serializable
data class SesionRespuesta(
    val token: String,
    val usuario: String,
    val nombre: String,
    val cuenta: String,
    val saldo: Double,
    val minutosInactividad: Int
)

// Quién hizo la petición (sale del token). Solo se usa dentro del servidor.
data class SesionUsuario(val usuarioId: Long, val usuario: String, val nombre: String, val cuenta: String)

// ======================== OPERACIONES ========================

// Pedido de DEPÓSITO o RETIRO: solo hace falta saber cuánto.
@Serializable
data class MontoRequest(val monto: Double)

// Pedido de TRANSFERENCIA: a qué cuenta y cuánto.
@Serializable
data class TransferenciaRequest(val cuentaDestino: String, val monto: Double)

// Pedido de PAGO DE SERVICIO: qué servicio y qué contrato (el monto sale de la factura).
@Serializable
data class PagoServicioRequest(val servicio: String, val contrato: String)

// Datos de la cuenta del usuario: número, dueño y saldo.
@Serializable
data class CuentaRespuesta(val numero: String, val titular: String, val saldo: Double)

// Dueño de otra cuenta, para confirmar a quién se transfiere (nunca su saldo).
@Serializable
data class DestinatarioRespuesta(val numero: String, val titular: String)

// Una FACTURA de servicio básico. Los últimos datos solo aparecen si existen.
@Serializable
data class ContratoRespuesta(
    val codigo: String,
    val servicio: String,
    val titular: String,
    val monto: Double,
    val pagado: Boolean,
    val cedula: String? = null,
    val periodo: String? = null,
    val pagadoPor: String? = null,
    val fechaPago: Long? = null
)

// Datos para emitir una factura nueva (solo el laboratorio).
@Serializable
data class NuevaFacturaRequest(
    val cedula: String,
    val titular: String,
    val servicio: String,
    val periodo: String,
    val monto: Double
)

// Un servicio del catálogo (ej. LUZ, "Luz eléctrica", ícono RAYO).
@Serializable
data class ServicioRespuesta(val codigo: String, val nombre: String, val icono: String)

// Datos para agregar un servicio nuevo (solo el laboratorio).
@Serializable
data class NuevoServicioRequest(val codigo: String, val nombre: String, val icono: String)

// Aviso sencillo al laboratorio (ej. "FACTURAS_ACTUALIZADAS").
@Serializable
data class EventoAviso(val tipo: String, val detalle: String? = null)

// Respuesta cuando una operación sale bien: comprobante y saldo nuevo.
@Serializable
data class OperacionRespuesta(
    val transaccionId: Long,
    val tipo: String,
    val cuenta: String,
    val monto: Double,
    val saldo: Double,
    val fecha: Long,
    val mensaje: String
)

// Un movimiento del historial, visto desde la cuenta del usuario.
@Serializable
data class MovimientoRespuesta(
    val transaccionId: Long,
    val tipo: String,
    val monto: Double,
    val fecha: Long,
    val detalle: String,
    val categoria: String? = null
)

// Aviso en tiempo real para el celular (ej. "te llegó una transferencia").
@Serializable
data class EventoWebSocket(
    val tipo: String,
    val cuenta: String,
    val saldo: Double,
    val cuentaOrigen: String? = null,
    val monto: Double? = null,
    val transaccionId: Long? = null,
    val detalle: String? = null,
    val fecha: Long = System.currentTimeMillis()
)

// Aviso al laboratorio: cuántos celulares están conectados y de qué cuentas.
@Serializable
data class EventoConexiones(
    val tipo: String = "CONEXIONES",
    val total: Int,
    val cuentas: List<String>
)

// Forma de una respuesta de error: un código y un mensaje.
@Serializable
data class ErrorRespuesta(val codigo: String, val mensaje: String)

// ======================== ERRORES ========================

// Un error que ya sabe qué código devolver (400, 401, 404...). Application.kt lo responde a la app.
class ErrorNegocio(
    val estado: HttpStatusCode,
    val codigo: String,
    override val message: String
) : Exception(message)

// Lista de errores posibles. Se usan así: throw montoInvalido()
// 400: datos mal enviados.
fun montoInvalido() = ErrorNegocio(HttpStatusCode.BadRequest, "MONTO_INVALIDO", "El monto debe ser mayor que 0")
fun datoInvalido(mensaje: String) = ErrorNegocio(HttpStatusCode.BadRequest, "DATO_INVALIDO", mensaje)
// 401: no hay sesión válida.
fun noAutenticado() = ErrorNegocio(HttpStatusCode.Unauthorized, "NO_AUTENTICADO", "Debes iniciar sesión")
fun sesionExpirada() = ErrorNegocio(HttpStatusCode.Unauthorized, "SESION_EXPIRADA",
    "Tu sesión se cerró por inactividad. Inicia sesión de nuevo")
fun credencialesInvalidas() = ErrorNegocio(HttpStatusCode.Unauthorized, "CREDENCIALES_INVALIDAS", "Usuario o contraseña incorrectos")
// 404: no existe.
fun cuentaNoExiste(numero: String) = ErrorNegocio(HttpStatusCode.NotFound, "CUENTA_NO_EXISTE", "La cuenta $numero no existe")
fun contratoNoExiste(codigo: String) = ErrorNegocio(HttpStatusCode.NotFound, "CONTRATO_NO_EXISTE", "El contrato $codigo no existe")
// 409: no se puede ahora (ej. no alcanza el saldo).
fun saldoInsuficiente(saldo: Double, monto: Double) = ErrorNegocio(HttpStatusCode.Conflict, "SALDO_INSUFICIENTE",
    "Saldo insuficiente: tienes $saldo y se requieren $monto")
fun usuarioExiste(usuario: String) = ErrorNegocio(HttpStatusCode.Conflict, "USUARIO_EXISTE", "El usuario '$usuario' ya está registrado")
// 403: tiene sesión, pero no tiene permiso.
fun sinPermiso() = ErrorNegocio(HttpStatusCode.Forbidden, "SIN_PERMISO", "Solo el usuario laboratorio puede administrar los servicios")
// Cédula inválida, servicio repetido, factura ya pagada o factura repetida (mismo mes).
fun cedulaInvalida() = ErrorNegocio(HttpStatusCode.BadRequest, "CEDULA_INVALIDA", "La cédula no es válida")
fun servicioExiste(codigo: String) = ErrorNegocio(HttpStatusCode.Conflict, "SERVICIO_EXISTE", "Ya existe un servicio con el código $codigo")
fun facturaPagada(codigo: String) = ErrorNegocio(HttpStatusCode.Conflict, "FACTURA_PAGADA", "La factura del contrato $codigo ya fue pagada")
fun facturaRepetida(servicio: String, periodo: String, codigo: String) = ErrorNegocio(HttpStatusCode.Conflict, "FACTURA_REPETIDA",
    "Esta cédula ya tiene una factura de $servicio de $periodo (contrato $codigo)")
