// Paquete del servidor.
package com.billetera.servidor

import io.ktor.http.HttpStatusCode           // Códigos HTTP (200, 400, 404, 409...).
import kotlinx.serialization.Serializable    // Marca una clase como convertible a/desde JSON.

// =====================================================================
// PETICIONES (JSON que ENVÍA la app o Postman)
// "@Serializable" = el plugin de serialización genera el código JSON <-> objeto.
// =====================================================================

// POST /depositar   y   POST /retirar
// Ejemplo JSON: { "cuenta": "1001", "monto": 50.0 }
@Serializable
data class OperacionSimpleRequest(
    val cuenta: String,     // Número de cuenta.
    val monto: Double       // Cantidad de dinero.
)

// POST /transferir
// Ejemplo JSON: { "cuentaOrigen": "1002", "cuentaDestino": "1001", "monto": 25.0 }
@Serializable
data class TransferenciaRequest(
    val cuentaOrigen: String,   // Quién envía.
    val cuentaDestino: String,  // Quién recibe.
    val monto: Double
)

// POST /pagar-servicio
// Ejemplo JSON: { "cuenta": "1001", "servicio": "LUZ", "contrato": "445566", "monto": 32.75 }
@Serializable
data class PagoServicioRequest(
    val cuenta: String,
    val servicio: String,       // "LUZ", "AGUA" o "INTERNET".
    val contrato: String,       // Código de contrato de la factura.
    val monto: Double
)

// =====================================================================
// RESPUESTAS (JSON que DEVUELVE el servidor)
// =====================================================================

// GET /cuentas/{numero}
@Serializable
data class CuentaRespuesta(
    val numero: String,
    val titular: String,
    val saldo: Double
)

// Respuesta de toda operación exitosa (depósito, retiro, transferencia, pago).
@Serializable
data class OperacionRespuesta(
    val transaccionId: Long,    // Id del comprobante en MySQL.
    val tipo: String,           // "DEPOSITO", "RETIRO", "TRANSFERENCIA", "PAGO_SERVICIO".
    val cuenta: String,         // Cuenta que hizo la operación.
    val monto: Double,
    val saldo: Double,          // SALDO NUEVO de la cuenta tras la operación.
    val fecha: Long,            // Fecha en milisegundos.
    val mensaje: String         // Texto para mostrar al usuario.
)

// Mensaje que el servidor ENVÍA por WebSocket.
// tipo = "CONECTADO" (al conectarse, con el saldo actual)
//      o "TRANSFERENCIA_RECIBIDA" (cuando otra cuenta le envía dinero).
// "String? = null" = campos opcionales (null safety): CONECTADO no lleva cuentaOrigen.
@Serializable
data class EventoWebSocket(
    val tipo: String,
    val cuenta: String,             // Cuenta que recibe el mensaje.
    val saldo: Double,              // Saldo actualizado.
    val cuentaOrigen: String? = null,
    val monto: Double? = null,
    val transaccionId: Long? = null,
    val fecha: Long = System.currentTimeMillis()
)

// Cuerpo JSON de una respuesta de ERROR.
// Ejemplo: { "codigo": "SALDO_INSUFICIENTE", "mensaje": "Saldo insuficiente: ..." }
@Serializable
data class ErrorRespuesta(
    val codigo: String,
    val mensaje: String
)

// =====================================================================
// ERRORES DE NEGOCIO
// Una excepción propia que ya sabe qué código HTTP le corresponde.
// El plugin StatusPages (Application.kt) la convierte en la respuesta HTTP.
// =====================================================================
class ErrorNegocio(
    val estado: HttpStatusCode,     // Código HTTP a devolver.
    val codigo: String,             // Código corto para que la app lo interprete.
    override val message: String    // Mensaje legible.
) : Exception(message)

// Funciones de ayuda para crear cada tipo de error con una sola línea.
fun montoInvalido() = ErrorNegocio(HttpStatusCode.BadRequest, "MONTO_INVALIDO", "El monto debe ser mayor que 0")
fun cuentaNoExiste(numero: String) = ErrorNegocio(HttpStatusCode.NotFound, "CUENTA_NO_EXISTE", "La cuenta $numero no existe")
fun saldoInsuficiente(saldo: Double, monto: Double) = ErrorNegocio(
    HttpStatusCode.Conflict,        // 409 = la petición choca con el estado actual (no hay fondos).
    "SALDO_INSUFICIENTE",
    "Saldo insuficiente: tienes $saldo y se requieren $monto"
)
fun datoInvalido(mensaje: String) = ErrorNegocio(HttpStatusCode.BadRequest, "DATO_INVALIDO", mensaje)
