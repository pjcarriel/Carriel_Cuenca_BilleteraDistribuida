// Paquete del servidor.
package com.billetera.servidor

import io.ktor.http.HttpStatusCode           // Códigos HTTP (200, 400, 401, 404, 409...).
import kotlinx.serialization.Serializable    // Marca una clase como convertible a/desde JSON.

// =====================================================================
// AUTENTICACIÓN
// =====================================================================

// POST /auth/registro   {"usuario":"maria","nombre":"María López","password":"clave123"}
@Serializable
data class RegistroRequest(val usuario: String, val nombre: String, val password: String)

// POST /auth/login   {"usuario":"pamela","password":"demo123"}
@Serializable
data class LoginRequest(val usuario: String, val password: String)

// Respuesta de registro y login: el TOKEN más los datos de la cuenta.
@Serializable
data class SesionRespuesta(
    val token: String,              // Se envía en cada petición: Authorization: Bearer <token>
    val usuario: String,
    val nombre: String,
    val cuenta: String,             // Número de cuenta asignado.
    val saldo: Double,
    val minutosInactividad: Int     // Tras cuántos minutos sin actividad expira la sesión.
)

// Quién hizo la petición (lo obtenemos del token; NO viaja en el JSON).
// No lleva @Serializable porque nunca se convierte a JSON: solo se usa dentro del servidor.
data class SesionUsuario(val usuarioId: Long, val usuario: String, val nombre: String, val cuenta: String)

// =====================================================================
// OPERACIONES (la cuenta de ORIGEN siempre sale del token)
// =====================================================================

// POST /depositar  y  POST /retirar   {"monto": 50.0}
@Serializable
data class MontoRequest(val monto: Double)

// POST /transferir   {"cuentaDestino":"1001","monto":25.0}
@Serializable
data class TransferenciaRequest(val cuentaDestino: String, val monto: Double)

// POST /pagar-servicio   {"servicio":"LUZ","contrato":"445566"}  (el monto lo pone la FACTURA)
@Serializable
data class PagoServicioRequest(val servicio: String, val contrato: String)

// GET /mi-cuenta
@Serializable
data class CuentaRespuesta(val numero: String, val titular: String, val saldo: Double)

// GET /cuentas/{numero}: solo el titular (para confirmar a quién se transfiere; nunca el saldo ajeno).
@Serializable
data class DestinatarioRespuesta(val numero: String, val titular: String)

// GET /contratos/{codigo}: la factura de un servicio básico.
@Serializable
data class ContratoRespuesta(
    val codigo: String,
    val servicio: String,           // "LUZ", "AGUA" o "INTERNET".
    val titular: String,
    val monto: Double,              // Lo que se debe pagar.
    val pagado: Boolean
)

// Respuesta de toda operación exitosa.
@Serializable
data class OperacionRespuesta(
    val transaccionId: Long,
    val tipo: String,
    val cuenta: String,
    val monto: Double,
    val saldo: Double,              // SALDO NUEVO de la cuenta tras la operación.
    val fecha: Long,
    val mensaje: String
)

// GET /movimientos: un comprobante visto desde la cuenta del usuario.
// tipo usa los MISMOS nombres que el enum de la app: DEPOSITO, RETIRO, TRANSFERENCIA_ENVIADA,
// TRANSFERENCIA_RECIBIDA, PAGO_SERVICIO.
@Serializable
data class MovimientoRespuesta(
    val transaccionId: Long,
    val tipo: String,
    val monto: Double,
    val fecha: Long,
    val detalle: String,
    val categoria: String? = null   // Solo los pagos: "Servicio Básico".
)

// Mensaje que el servidor ENVÍA por WebSocket.
@Serializable
data class EventoWebSocket(
    val tipo: String,               // "CONECTADO" o "TRANSFERENCIA_RECIBIDA".
    val cuenta: String,
    val saldo: Double,
    val cuentaOrigen: String? = null,
    val monto: Double? = null,
    val transaccionId: Long? = null,
    val detalle: String? = null,    // Ej. "De Usuario Postman A (1002)".
    val fecha: Long = System.currentTimeMillis()
)

// Mensaje por WebSocket SOLO para el laboratorio: cuántos nodos (dispositivos) están conectados.
// Ej. {"tipo":"CONEXIONES","total":3,"cuentas":["1001","1002","9999"]}
@Serializable
data class EventoConexiones(
    val tipo: String = "CONEXIONES",
    val total: Int,                 // Dispositivos con el WebSocket abierto (una cuenta puede tener varios).
    val cuentas: List<String>       // Cuentas distintas conectadas, ordenadas.
)

// Cuerpo JSON de una respuesta de ERROR.
@Serializable
data class ErrorRespuesta(val codigo: String, val mensaje: String)

// =====================================================================
// ERRORES DE NEGOCIO: excepción que ya sabe su código HTTP
// =====================================================================
// Hereda de Exception, así que se puede lanzar con "throw". StatusPages (Application.kt) la atrapa
// y responde con "estado" y un JSON {"codigo": ..., "mensaje": ...}.
// "override val message" reemplaza el mensaje estándar de Exception por el nuestro.
class ErrorNegocio(
    val estado: HttpStatusCode,
    val codigo: String,
    override val message: String
) : Exception(message)

// Funciones "fábrica": cada una CREA (no lanza) el error; quien la usa escribe "throw montoInvalido()".
// --- 400: datos inválidos ---
fun montoInvalido() = ErrorNegocio(HttpStatusCode.BadRequest, "MONTO_INVALIDO", "El monto debe ser mayor que 0")
fun datoInvalido(mensaje: String) = ErrorNegocio(HttpStatusCode.BadRequest, "DATO_INVALIDO", mensaje)
// --- 401: sin sesión válida ---
fun noAutenticado() = ErrorNegocio(HttpStatusCode.Unauthorized, "NO_AUTENTICADO", "Debes iniciar sesión")
fun sesionExpirada() = ErrorNegocio(HttpStatusCode.Unauthorized, "SESION_EXPIRADA",
    "Tu sesión se cerró por inactividad. Inicia sesión de nuevo")
// Mensaje genérico a propósito: no revelamos si falló el usuario o la contraseña.
fun credencialesInvalidas() = ErrorNegocio(HttpStatusCode.Unauthorized, "CREDENCIALES_INVALIDAS", "Usuario o contraseña incorrectos")
// --- 404: no existe ---
fun cuentaNoExiste(numero: String) = ErrorNegocio(HttpStatusCode.NotFound, "CUENTA_NO_EXISTE", "La cuenta $numero no existe")
fun contratoNoExiste(codigo: String) = ErrorNegocio(HttpStatusCode.NotFound, "CONTRATO_NO_EXISTE", "El contrato $codigo no existe")
// --- 409: choca con el estado actual ---
fun saldoInsuficiente(saldo: Double, monto: Double) = ErrorNegocio(HttpStatusCode.Conflict, "SALDO_INSUFICIENTE",
    "Saldo insuficiente: tienes $saldo y se requieren $monto")
fun usuarioExiste(usuario: String) = ErrorNegocio(HttpStatusCode.Conflict, "USUARIO_EXISTE", "El usuario '$usuario' ya está registrado")
fun facturaPagada(codigo: String) = ErrorNegocio(HttpStatusCode.Conflict, "FACTURA_PAGADA", "La factura del contrato $codigo ya fue pagada")
