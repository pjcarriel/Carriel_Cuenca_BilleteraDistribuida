// Paquete de la base de datos local.
package com.example.carriel_cuenca_billeteradistribuida.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// Consultas de la tabla cuenta_local (el último saldo conocido).
@Dao
interface CuentaDao {

    // Guarda el saldo. REPLACE: si la cuenta ya tiene fila, la REEMPLAZA (no se duplica).
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(cuenta: CuentaLocalEntity)

    // Observa el saldo de una cuenta. ":numero" se reemplaza por el parámetro.
    // "Double?" = puede ser null si todavía nunca se obtuvo el saldo del servidor.
    // Como es Flow, cada vez que guardar() cambia el saldo, la pantalla se actualiza sola.
    @Query("SELECT saldo FROM cuenta_local WHERE numero = :numero")
    fun observarSaldo(numero: String): Flow<Double?>
}
