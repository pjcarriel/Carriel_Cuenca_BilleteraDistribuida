// Paquete de la base de datos local.
package com.example.carriel_cuenca_billeteradistribuida.data.local

import androidx.room.Dao                 // Anotación: "esta interfaz contiene consultas".
import androidx.room.Insert              // Anotación para insertar filas.
import androidx.room.Query               // Anotación para escribir una consulta SQL.
import kotlinx.coroutines.flow.Flow      // Flujo de datos que emite valores con el tiempo.

// DAO = Data Access Object (Objeto de Acceso a Datos).
// Es una "interface": solo DECLARAMOS qué consultas queremos, sin escribir el código.
// KSP genera al compilar una clase "MovimientoDao_Impl" con todo el código SQLite real.
@Dao
interface MovimientoDao {

    // Inserta un movimiento y devuelve el id que Room le asignó.
    // "suspend" = función SUSPENDIBLE: puede pausarse mientras escribe en disco
    // sin bloquear el hilo. Solo se puede llamar desde una corrutina.
    @Insert
    suspend fun insertar(movimiento: MovimientoEntity): Long

    // Devuelve TODO el historial, el más reciente primero.
    // No es "suspend" porque devuelve un Flow: un "tubo" que queda abierto.
    // Cada vez que la tabla cambia (alguien inserta), Room vuelve a ejecutar
    // la consulta y EMITE la lista nueva automáticamente. ¡Historial reactivo!
    @Query("SELECT * FROM movimientos ORDER BY fechaHora DESC")
    fun observarTodos(): Flow<List<MovimientoEntity>>
}
