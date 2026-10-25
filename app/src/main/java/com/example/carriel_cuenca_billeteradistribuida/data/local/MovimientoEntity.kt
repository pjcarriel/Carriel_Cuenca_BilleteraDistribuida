// =====================================================================
//   Es la tabla del teléfono donde se guarda el historial de movimientos.
//   Cada fila dice a qué cuenta pertenece.
// =====================================================================

package com.example.carriel_cuenca_billeteradistribuida.data.local

import androidx.room.Entity       // Marca "esta clase es una TABLA".
import androidx.room.Index        // Índice: busca rápido y puede impedir repetidos.
import androidx.room.PrimaryKey   // Marca "este campo es la llave de la tabla".
import com.example.carriel_cuenca_billeteradistribuida.model.Movimiento
import com.example.carriel_cuenca_billeteradistribuida.model.TipoOperacion

// Un movimiento guardado. No se puede repetir la misma cuenta + transaccionId (evita duplicados).
@Entity(
    tableName = "movimientos",
    indices = [Index(value = ["cuenta", "transaccionId"], unique = true)]
)
data class MovimientoEntity(
    // Número de la fila; la base de datos lo pone sola (1, 2, 3...).
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val cuenta: String,                 // Cuenta dueña del movimiento (ej. "1001").
    val transaccionId: Long?,           // Número que le dio el servidor (vacío si no tiene).
    val tipo: TipoOperacion,            // Depósito, retiro, etc.
    val monto: Double,
    val fechaHora: Long,
    val detalle: String,
    val categoria: String?
)

// Fila de la tabla -> Movimiento de la app (para mostrarlo en el historial).
// Mostrar en pantalla
// Como ya se visualiza pordemos ver la cuenta y no es necesario
fun MovimientoEntity.aModelo(): Movimiento = Movimiento(
    id = id,
    tipo = tipo,
    monto = monto,
    fechaHora = fechaHora,
    detalle = detalle,
    categoria = categoria,
    transaccionId = transaccionId
)

// Movimiento de la app -> fila de la tabla, indicando de qué cuenta es (para guardarlo).
// Guardar en el room
// El room necesita saber las cuentas para guardar de quien son cada una
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
