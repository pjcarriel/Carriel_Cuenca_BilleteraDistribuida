// Paquete de la base de datos local.
package com.example.carriel_cuenca_billeteradistribuida.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy      // Qué hacer si se viola el índice único.
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// Consultas de la tabla "movimientos".
@Dao
interface MovimientoDao {

    // Inserta un movimiento. IGNORE: si ya existe (misma cuenta y transaccionId), no hace nada
    // y devuelve -1. Así un comprobante repetido no aparece dos veces en el historial.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertar(movimiento: MovimientoEntity): Long

    // Inserta muchos de una vez (historial descargado del servidor), ignorando los repetidos.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertarTodos(movimientos: List<MovimientoEntity>): List<Long>

    // Historial de UNA cuenta, el más reciente primero. Flow: se vuelve a emitir con cada cambio.
    @Query("SELECT * FROM movimientos WHERE cuenta = :cuenta ORDER BY fechaHora DESC")
    fun observarTodos(cuenta: String): Flow<List<MovimientoEntity>>
}
