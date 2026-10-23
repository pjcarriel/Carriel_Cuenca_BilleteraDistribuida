// Paquete de la base de datos local.
package com.example.carriel_cuenca_billeteradistribuida.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

// Consultas de la tabla "sesion".
@Dao
interface SesionDao {

    // La sesión guardada, o null si no hay ninguna.
    @Query("SELECT * FROM sesion WHERE id = 0")
    suspend fun obtener(): SesionEntity?

    // Guarda (o reemplaza) la sesión. Como el id siempre es 0, REPLACE pisa la sesión anterior.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(sesion: SesionEntity)

    // Actualiza solo el momento de la última actividad.
    @Query("UPDATE sesion SET ultimaActividad = :momento WHERE id = 0")
    suspend fun actualizarActividad(momento: Long)

    // Borra la sesión (cerrar sesión). Sin WHERE: borra todas las filas (como máximo hay una).
    @Query("DELETE FROM sesion")
    suspend fun borrar()
}
