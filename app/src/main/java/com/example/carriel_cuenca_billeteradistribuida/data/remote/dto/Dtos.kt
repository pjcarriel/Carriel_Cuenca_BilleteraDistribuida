// DTO = Data Transfer Object: clases que representan EXACTAMENTE el JSON que viaja por la red.
// Gson convierte JSON <-> estas data class usando los NOMBRES de las propiedades,
// por eso deben llamarse igual que los campos del JSON del servidor.
package com.example.carriel_cuenca_billeteradistribuida.data.remote.dto

// ---------- Lo que ENVIAMOS al servidor ----------

// Cuerpo de POST /depositar y POST /retirar.  JSON: {"cuenta":"1001","monto":50.0}
data class OperacionSimpleRequest(
    val cuenta: String,
    val monto: Double
)

// Cuerpo de POST /transferir.  JSON: {"cuentaOrigen":"1001","cuentaDestino":"1002","monto":25.0}
data class TransferenciaRequest(
    val cuentaOrigen: String,
    val cuentaDestino: String,
    val monto: Double
)

// Cuerpo de POST /pagar-servicio.  JSON: {"cuenta":"1001","servicio":"LUZ","contrato":"445566","monto":32.75}
data class PagoServicioRequest(
    val cuenta: String,
    val servicio: String,
    val contrato: String,
    val monto: Double
)

// ---------- Lo que RECIBIMOS del servidor ----------

// Respuesta de GET /cuentas/{numero}.
data class CuentaDto(
    val numero: String,
    val titular: String,
    val saldo: Double
)

// Respuesta de las 4 operaciones cuando salen BIEN (HTTP 200).
data class OperacionRespuestaDto(
    val transaccionId: Long,    // Id del comprobante en MySQL.
    val tipo: String,
    val cuenta: String,
    val monto: Double,
    val saldo: Double,          // SALDO NUEVO después de la operación.
    val fecha: Long,            // Fecha del servidor en milisegundos.
    val mensaje: String         // Texto para mostrar al usuario.
)

// Mensaje que llega por el WEBSOCKET (no por REST).
// tipo = "CONECTADO" -> al abrir el túnel, con el saldo actual.
// tipo = "TRANSFERENCIA_RECIBIDA" -> otra cuenta nos envió dinero.
// Los campos con "?" no vienen en todos los mensajes (CONECTADO no trae cuentaOrigen ni monto).
data class EventoWebSocketDto(
    val tipo: String,
    val cuenta: String,
    val saldo: Double,              // Saldo actualizado de NUESTRA cuenta.
    val cuentaOrigen: String?,      // Quién envió el dinero.
    val monto: Double?,             // Cuánto envió.
    val transaccionId: Long?,
    val fecha: Long
)

// Cuerpo de las respuestas de ERROR (400, 404, 409).
// JSON: {"codigo":"SALDO_INSUFICIENTE","mensaje":"Saldo insuficiente: ..."}
data class ErrorRespuestaDto(
    val codigo: String?,        // "?" porque si el servidor falla raro podría no venir.
    val mensaje: String?
)
