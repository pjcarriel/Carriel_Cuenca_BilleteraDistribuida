// =====================================================================
//   Son las órdenes para guardar, leer y borrar la sesión que se guarda
//   en el teléfono.
// =====================================================================

package com.example.carriel_cuenca_billeteradistribuida.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

// Lista de órdenes de la tabla "sesion".
@Dao
interface SesionDao {

    // Trae la sesión guardada, o vacío si no hay (entonces se muestra el login).
    @Query("SELECT * FROM sesion WHERE id = 0")
    suspend fun obtener(): SesionEntity?

    // Guarda la sesión; la nueva reemplaza a la anterior.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(sesion: SesionEntity)

    // Anota la hora en que el usuario usó la app por última vez.
    @Query("UPDATE sesion SET ultimaActividad = :momento WHERE id = 0")
    suspend fun actualizarActividad(momento: Long)

    // Borra la sesión (al cerrar sesión).
    @Query("DELETE FROM sesion")
    suspend fun borrar()
}
