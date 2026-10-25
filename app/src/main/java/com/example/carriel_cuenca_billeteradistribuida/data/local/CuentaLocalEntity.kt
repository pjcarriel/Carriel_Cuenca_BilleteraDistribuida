// =====================================================================
//   Es la tabla del teléfono donde se anota el ÚLTIMO SALDO que dio el
//   servidor, para mostrarlo aunque no haya internet.
// =====================================================================

package com.example.carriel_cuenca_billeteradistribuida.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

// Una fila por cuenta con su último saldo conocido.
@Entity(tableName = "cuenta_local")
data class CuentaLocalEntity(
    // Número de cuenta (la llave de la tabla).
    @PrimaryKey val numero: String,
    // Saldo que dijo el servidor.
    val saldo: Double,
    // Cuándo se guardó.
    val actualizado: Long
)
