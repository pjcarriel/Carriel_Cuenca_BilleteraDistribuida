// =====================================================================
//   Son los "sobres" con los que la app y el servidor se mandan datos:
//   cada clase tiene la misma forma que el JSON que viaja por la red.
// =====================================================================

package com.example.carriel_cuenca_billeteradistribuida.data.remote.dto

// =========================== INICIO DE SESIÓN ===========================

// Datos para entrar. Ej: {"usuario":"pamela","password":"demo123"}
data class LoginRequest(val usuario: String, val password: String)

// Datos para crear una cuenta.
data class RegistroRequest(val usuario: String, val nombre: String, val password: String)

// Respuesta al entrar o registrarse.
data class SesionDto(
    val token: String,              // El pase de la sesión.
    val usuario: String,
    val nombre: String,
    val cuenta: String,             // Número de cuenta.
    val saldo: Double,
    val minutosInactividad: Int     // Minutos sin uso antes de cerrar la sesión sola.
)

// =========================== OPERACIONES ===========================
// Nuestra cuenta no se envía: el servidor la sabe por el token.

// Monto para depositar o retirar. Ej: {"monto": 50.0}
data class MontoRequest(val monto: Double)

// Datos para transferir: a qué cuenta y cuánto.
data class TransferenciaRequest(val cuentaDestino: String, val monto: Double)

// Datos para pagar un servicio: qué servicio y qué contrato (el monto lo pone la factura).
data class PagoServicioRequest(val servicio: String, val contrato: String)

// Mi cuenta: número, titular y saldo.
data class CuentaDto(val numero: String, val titular: String, val saldo: Double)

// Una factura de servicio.
data class ContratoDto(
    val codigo: String,
    val servicio: String,
    val titular: String,
    val monto: Double,
    val pagado: Boolean,
    // Pueden llegar vacíos (null).
    val cedula: String?,
    val periodo: String?,
    val pagadoPor: String?,
    val fechaPago: Long?
)

// Aviso por el túnel al laboratorio cuando cambian las facturas.
data class EventoAvisoDto(val tipo: String, val detalle: String?)

// Un servicio del catálogo. Ej: {"codigo":"LUZ","nombre":"Luz eléctrica","icono":"RAYO"}
data class ServicioDto(val codigo: String, val nombre: String, val icono: String)

// Datos para agregar un servicio (solo laboratorio).
data class NuevoServicioRequest(val codigo: String, val nombre: String, val icono: String)

// Datos para crear una factura (solo laboratorio).
data class NuevaFacturaRequest(
    val cedula: String,
    val titular: String,
    val servicio: String,
    val periodo: String,
    val monto: Double
)

// Un movimiento del historial, tal como llega del servidor.
data class MovimientoDto(
    val transaccionId: Long,
    val tipo: String,               // Mismos nombres que TipoOperacion.
    val monto: Double,
    val fecha: Long,
    val detalle: String,
    val categoria: String?
)

// Respuesta cuando una operación sale bien.
data class OperacionRespuestaDto(
    val transaccionId: Long,
    val tipo: String,
    val cuenta: String,
    val monto: Double,
    val saldo: Double,              // Saldo nuevo.
    val fecha: Long,
    val mensaje: String
)

// Aviso por el túnel: al conectarse o al recibir una transferencia.
data class EventoWebSocketDto(
    val tipo: String,               // "CONECTADO" o "TRANSFERENCIA_RECIBIDA".
    val cuenta: String,
    val saldo: Double,
    val cuentaOrigen: String?,
    val monto: Double?,
    val transaccionId: Long?,
    val detalle: String?,           // Ej. "De María López (1004)".
    val fecha: Long
)

// Aviso por el túnel al laboratorio: cuántas cuentas están conectadas y cuáles.
// "cuentas" puede llegar vacía (null).
data class EventoConexionesDto(
    val tipo: String,
    val total: Int,
    val cuentas: List<String>?
)

// Respuesta cuando algo sale mal (ej. saldo insuficiente).
data class ErrorRespuestaDto(val codigo: String?, val mensaje: String?)
