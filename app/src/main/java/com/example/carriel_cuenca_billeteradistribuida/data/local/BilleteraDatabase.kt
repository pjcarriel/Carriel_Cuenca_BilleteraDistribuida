// =====================================================================
//   Es la base de datos que vive DENTRO del teléfono. Tiene tres tablas:
//   el historial, el último saldo y la sesión abierta.
// =====================================================================

package com.example.carriel_cuenca_billeteradistribuida.data.local

import android.content.Context           // Da acceso a la carpeta de datos de la app.
import androidx.room.Database            // Marca "esta clase es la base de datos".
import androidx.room.Room                // Sirve para construir la base de datos.
import androidx.room.RoomDatabase        // Base de la que parte toda base de datos de Room.

// Las tres tablas, la versión (sube cuando cambian las tablas) y sin exportar el diseño.
@Database(entities = [MovimientoEntity::class, CuentaLocalEntity::class, SesionEntity::class], version = 3, exportSchema = false)
// Solo declaramos qué queremos; Room escribe el resto del código al compilar.
abstract class BilleteraDatabase : RoomDatabase() {

    // Acceso a la tabla del historial.
    abstract fun movimientoDao(): MovimientoDao

    // Acceso a la tabla del último saldo.
    abstract fun cuentaDao(): CuentaDao

    // Acceso a la tabla de la sesión.
    abstract fun sesionDao(): SesionDao

    // Se usa directamente con el nombre de la clase: BilleteraDatabase.obtener(context).
    companion object {

        // La ÚNICA base de datos de la app. @Volatile: todos los hilos ven el mismo valor.
        @Volatile
        private var instancia: BilleteraDatabase? = null

        // Abre la base de datos. La primera vez la crea; después devuelve la misma.
        fun obtener(context: Context): BilleteraDatabase {
            return instancia ?: synchronized(this) {
                // Solo un hilo a la vez entra aquí, así nunca se crean dos bases de datos.
                instancia ?: Room.databaseBuilder(
                    context.applicationContext,       // Contexto de toda la app.
                    BilleteraDatabase::class.java,    // Qué base de datos construir.
                    "billetera.db"                    // Nombre del archivo en el teléfono.
                )
                    // Si cambian las tablas, se borra y se crea de nuevo (el historial real está en el servidor).
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    // Se guarda para no volver a crearla.
                    .also { instancia = it }
            }
        }
    }
}
