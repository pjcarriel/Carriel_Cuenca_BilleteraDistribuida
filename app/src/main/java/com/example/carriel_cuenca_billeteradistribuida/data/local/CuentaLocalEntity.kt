// Paquete de la base de datos local.
package com.example.carriel_cuenca_billeteradistribuida.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

// Tabla "cuenta_local": guarda el ÚLTIMO SALDO CONOCIDO que respondió el servidor.
// Sirve para que, si no hay internet, la app muestre el saldo de la última vez
// en lugar de una pantalla vacía (la copia local del nodo cliente).
@Entity(tableName = "cuenta_local")
data class CuentaLocalEntity(
    // El número de cuenta es la llave: hay UNA fila por cuenta (la de la app: "1001").
    @PrimaryKey val numero: String,
    // Saldo según el servidor.
    val saldo: Double,
    // Cuándo se actualizó (milisegundos).
    val actualizado: Long
)
