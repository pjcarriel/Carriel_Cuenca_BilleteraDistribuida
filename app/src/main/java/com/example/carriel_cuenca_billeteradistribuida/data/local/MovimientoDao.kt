// =====================================================================
//   Son las órdenes para guardar y leer los movimientos del historial
//   que se guardan en el teléfono.
// =====================================================================

package com.example.carriel_cuenca_billeteradistribuida.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy      // Qué hacer si el movimiento ya estaba guardado.
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// Lista de órdenes de la tabla "movimientos"; Room crea el código que las ejecuta.
@Dao
interface MovimientoDao {

    // Guarda un movimiento. Si ya estaba, no lo repite (devuelve -1).
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertar(movimiento: MovimientoEntity): Long

    // Guarda de golpe el historial que baja del servidor, sin repetir.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertarTodos(movimientos: List<MovimientoEntity>): List<Long>

    // Historial de una cuenta, del más nuevo al más viejo. Se actualiza solo.
    @Query("SELECT * FROM movimientos WHERE cuenta = :cuenta ORDER BY fechaHora DESC")
    fun observarTodos(cuenta: String): Flow<List<MovimientoEntity>>

    // Cuántos movimientos tiene una cuenta (para el panel de pruebas).
    @Query("SELECT COUNT(*) FROM movimientos WHERE cuenta = :cuenta")
    suspend fun contar(cuenta: String): Int
}
