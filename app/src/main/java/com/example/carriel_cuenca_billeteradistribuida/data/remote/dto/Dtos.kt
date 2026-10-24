// DTO = Data Transfer Object: clases que representan EXACTAMENTE el JSON que viaja por la red.
// Gson convierte JSON <-> estas data class usando los NOMBRES de las propiedades.
package com.example.carriel_cuenca_billeteradistribuida.data.remote.dto

// =========================== AUTENTICACIÓN ===========================

// POST /auth/login   {"usuario":"pamela","password":"demo123"}
data class LoginRequest(val usuario: String, val password: String)

// POST /auth/registro   {"usuario":"maria","nombre":"María López","password":"maria2026"}
data class RegistroRequest(val usuario: String, val nombre: String, val password: String)

// Respuesta de login y registro.
data class SesionDto(
    val token: String,              // Se envía en cada petición (lo pone AuthInterceptor).
    val usuario: String,
    val nombre: String,
    val cuenta: String,             // Número de cuenta del usuario.
    val saldo: Double,
    val minutosInactividad: Int     // Minutos sin actividad tras los que la sesión se cierra.
)

// =========================== OPERACIONES ===========================
// La cuenta de ORIGEN no viaja en el JSON: el servidor la toma del token.

// POST /depositar  y  POST /retirar   {"monto": 50.0}
data class MontoRequest(val monto: Double)

// POST /transferir   {"cuentaDestino":"1002","monto":25.0}
data class TransferenciaRequest(val cuentaDestino: String, val monto: Double)

// POST /pagar-servicio   {"servicio":"LUZ","contrato":"445566"}   (el monto lo pone la factura)
data class PagoServicioRequest(val servicio: String, val contrato: String)

// GET /mi-cuenta
data class CuentaDto(val numero: String, val titular: String, val saldo: Double)

// GET /contratos/{codigo}?servicio=LUZ
data class ContratoDto(
    val codigo: String,
    val servicio: String,
    val titular: String,
    val monto: Double,
    val pagado: Boolean,
    // Fase 8: nullable porque el servidor no los envía si están vacíos (y Gson deja null).
    val cedula: String?,
    val periodo: String?,
    val pagadoPor: String?,
    val fechaPago: Long?
)

// Aviso simple por WEBSOCKET (laboratorio): {"tipo":"FACTURAS_ACTUALIZADAS","detalle":"La cuenta 1001 pagó LUZ 445568"}
data class EventoAvisoDto(val tipo: String, val detalle: String?)

// POST /admin/facturas (solo laboratorio): crear una factura nueva.
data class NuevaFacturaRequest(
    val cedula: String,
    val titular: String,
    val servicio: String,
    val periodo: String,
    val monto: Double
)

// GET /movimientos: un comprobante del historial del servidor.
data class MovimientoDto(
    val transaccionId: Long,
    val tipo: String,               // Mismos nombres que el enum TipoOperacion.
    val monto: Double,
    val fecha: Long,
    val detalle: String,
    val categoria: String?
)

// Respuesta de las 4 operaciones cuando salen BIEN (HTTP 200).
data class OperacionRespuestaDto(
    val transaccionId: Long,
    val tipo: String,
    val cuenta: String,
    val monto: Double,
    val saldo: Double,              // SALDO NUEVO después de la operación.
    val fecha: Long,
    val mensaje: String
)

// Mensaje que llega por el WEBSOCKET.
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

// Mensaje por WEBSOCKET solo para el laboratorio: nodos conectados.
// "cuentas" es nullable a propósito: Gson no respeta los tipos no-null de Kotlin y, si el campo
// faltara en el JSON, dejaría null; por eso el Repository usa cuentas.orEmpty().
// {"tipo":"CONEXIONES","total":3,"cuentas":["1001","1002","9999"]}
data class EventoConexionesDto(
    val tipo: String,
    val total: Int,
    val cuentas: List<String>?
)

// Cuerpo de las respuestas de ERROR (400, 401, 404, 409).
data class ErrorRespuestaDto(val codigo: String?, val mensaje: String?)
