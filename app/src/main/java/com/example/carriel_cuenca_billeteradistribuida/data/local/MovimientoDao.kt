// Paquete de la base de datos local.
package com.example.carriel_cuenca_billeteradistribuida.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy      // Qué hacer si se viola el índice único.
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// Consultas de la tabla "movimientos".
// DAO = Data Access Object. Solo escribimos la INTERFAZ con anotaciones; Room genera el código real.
// Las funciones "suspend" Room las ejecuta fuera del hilo principal; las que devuelven Flow
// no son suspend porque solo crean el Flow (la lectura ocurre al hacer collect).
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

    // Cuenta cuántos movimientos tiene una cuenta. Lectura única (suspend), usada en el panel de pruebas.
    @Query("SELECT COUNT(*) FROM movimientos WHERE cuenta = :cuenta")
    suspend fun contar(cuenta: String): Int
}
