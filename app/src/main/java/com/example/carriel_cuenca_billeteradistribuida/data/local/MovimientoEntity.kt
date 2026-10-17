// Paquete "data.local": todo lo que se guarda DENTRO del teléfono (Room).
package com.example.carriel_cuenca_billeteradistribuida.data.local

import androidx.room.Entity       // Anotación: "esta clase es una TABLA".
import androidx.room.Index        // Índice: búsqueda rápida y, si es "unique", impide duplicados.
import androidx.room.PrimaryKey   // Anotación: "este campo es la llave primaria".
import com.example.carriel_cuenca_billeteradistribuida.model.Movimiento
import com.example.carriel_cuenca_billeteradistribuida.model.TipoOperacion

// Tabla "movimientos". Desde la Fase 6.5 guarda el historial de VARIAS cuentas (una por usuario
// que haya iniciado sesión en este teléfono), por eso cada fila sabe a qué "cuenta" pertenece.
// Índice ÚNICO (cuenta, transaccionId): el mismo comprobante del servidor no se puede guardar dos veces
// (llega por WebSocket y luego otra vez al descargar el historial -> la segunda se ignora).
@Entity(
    tableName = "movimientos",
    indices = [Index(value = ["cuenta", "transaccionId"], unique = true)]
)
data class MovimientoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val cuenta: String,                 // Cuenta dueña del movimiento (ej. "1001").
    val transaccionId: Long?,           // Id en MySQL (null si no lo hay).
    val tipo: TipoOperacion,            // Room guarda el enum como texto.
    val monto: Double,
    val fechaHora: Long,
    val detalle: String,
    val categoria: String?
)

// Fila de la tabla -> modelo de la app (la pantalla no necesita saber de qué cuenta es).
fun MovimientoEntity.aModelo(): Movimiento = Movimiento(
    id = id,
    tipo = tipo,
    monto = monto,
    fechaHora = fechaHora,
    detalle = detalle,
    categoria = categoria,
    transaccionId = transaccionId
)

// Modelo de la app -> fila de la tabla, indicando de qué cuenta es.
fun Movimiento.aEntidad(cuenta: String): MovimientoEntity = MovimientoEntity(
    id = id,
    cuenta = cuenta,
    transaccionId = transaccionId,
    tipo = tipo,
    monto = monto,
    fechaHora = fechaHora,
    detalle = detalle,
    categoria = categoria
)
