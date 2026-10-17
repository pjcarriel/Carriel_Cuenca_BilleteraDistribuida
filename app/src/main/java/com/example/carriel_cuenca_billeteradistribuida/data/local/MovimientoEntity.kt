// Paquete "data.local": todo lo que se guarda DENTRO del teléfono (Room).
package com.example.carriel_cuenca_billeteradistribuida.data.local

import androidx.room.Entity       // Anotación: "esta clase es una TABLA".
import androidx.room.PrimaryKey   // Anotación: "este campo es la llave primaria".
import com.example.carriel_cuenca_billeteradistribuida.model.Movimiento
import com.example.carriel_cuenca_billeteradistribuida.model.TipoOperacion

// @Entity convierte esta data class en una TABLA de SQLite llamada "movimientos".
// Cada propiedad (val) se convierte en una COLUMNA, y cada objeto en una FILA.
// KSP lee esta anotación al compilar y genera el SQL "CREATE TABLE ..." por nosotros.
@Entity(tableName = "movimientos")
data class MovimientoEntity(
    // Llave primaria. autoGenerate = true: Room asigna 1, 2, 3... automáticamente.
    // Por eso al insertar le pasamos 0 (valor por defecto) y Room pone el número real.
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Room guarda los enum automáticamente como TEXTO (ej. "DEPOSITO")
    // y al leer los convierte de vuelta al enum. No necesitamos convertidores.
    val tipo: TipoOperacion,

    // Columna REAL (número con decimales).
    val monto: Double,

    // Columna INTEGER con la fecha en milisegundos (se ordena fácilmente).
    val fechaHora: Long,

    // Columna TEXT con la cuenta o el contrato.
    val detalle: String,

    // Columna TEXT que ACEPTA NULL (por el "?"): solo los pagos de servicio la llenan.
    val categoria: String?
)

// ---------------------------------------------------------------------
// FUNCIONES DE EXTENSIÓN: "agregan" una función a una clase sin modificarla.
// "fun MovimientoEntity.aModelo()" se usa así: entidad.aModelo()
// ¿Por qué dos clases? MovimientoEntity es "cómo se guarda" (Room);
// Movimiento es "cómo lo usa la app" (pantalla). Así la UI no depende de Room.
// ---------------------------------------------------------------------

// Convierte una fila de la tabla en el modelo que usa la pantalla.
fun MovimientoEntity.aModelo(): Movimiento = Movimiento(
    id = id,                 // "id" aquí se refiere al id de ESTA entidad (this.id).
    tipo = tipo,
    monto = monto,
    fechaHora = fechaHora,
    detalle = detalle,
    categoria = categoria
)

// Convierte el modelo de la app en una fila para guardar en la tabla.
fun Movimiento.aEntidad(): MovimientoEntity = MovimientoEntity(
    id = id,
    tipo = tipo,
    monto = monto,
    fechaHora = fechaHora,
    detalle = detalle,
    categoria = categoria
)
