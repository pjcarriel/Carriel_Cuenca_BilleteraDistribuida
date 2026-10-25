// =====================================================================
//   Son las órdenes para guardar y leer el último saldo conocido que se
//   guarda en el teléfono.
// =====================================================================

package com.example.carriel_cuenca_billeteradistribuida.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// Lista de órdenes de la tabla "cuenta_local"; Room crea el código que las ejecuta.
@Dao
interface CuentaDao {

    // Guarda el saldo. Si la cuenta ya tenía uno, lo reemplaza.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(cuenta: CuentaLocalEntity)

    // Saldo de una cuenta (vacío si aún no hay). La pantalla se actualiza sola cuando cambia.
    @Query("SELECT saldo FROM cuenta_local WHERE numero = :numero")
    fun observarSaldo(numero: String): Flow<Double?>
}
