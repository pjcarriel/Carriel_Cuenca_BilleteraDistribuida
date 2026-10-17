// Paquete de la base de datos local.
package com.example.carriel_cuenca_billeteradistribuida.data.local

import android.content.Context           // Acceso a recursos de la app (aquí: la carpeta de datos).
import androidx.room.Database            // Anotación: "esta clase es la base de datos".
import androidx.room.Room                // Constructor de bases de datos Room.
import androidx.room.RoomDatabase        // Clase base de toda base de datos Room.

// @Database describe la base de datos:
//   entities = las tablas: "movimientos", "cuenta_local" (último saldo conocido) y "sesion".
//   version = número de versión: 2 en la Fase 4 (cuenta_local) y 3 en la Fase 6.5 (sesión y
//   movimientos por cuenta).
//   exportSchema = false: no exportar el esquema a un archivo JSON (no lo necesitamos).
@Database(entities = [MovimientoEntity::class, CuentaLocalEntity::class, SesionEntity::class], version = 3, exportSchema = false)
// "abstract" = clase incompleta: KSP genera la versión completa ("BilleteraDatabase_Impl").
abstract class BilleteraDatabase : RoomDatabase() {

    // Room implementa esta función y nos entrega el DAO listo para usar.
    abstract fun movimientoDao(): MovimientoDao

    // DAO de la tabla del último saldo conocido.
    abstract fun cuentaDao(): CuentaDao

    // DAO de la sesión guardada.
    abstract fun sesionDao(): SesionDao

    // "companion object" = lo que va aquí pertenece a la CLASE, no a un objeto.
    // Se usa así: BilleteraDatabase.obtener(context)  (parecido a "static" en Java).
    companion object {

        // Aquí guardamos la ÚNICA instancia de la base de datos (patrón Singleton).
        // "BilleteraDatabase?" = puede ser null (todavía no creada).
        // @Volatile = si un hilo la crea, los demás hilos lo ven al instante
        // (sin esto, un hilo podría tener una copia vieja en caché).
        @Volatile
        private var instancia: BilleteraDatabase? = null

        // Devuelve la base de datos; si no existe, la crea UNA sola vez.
        fun obtener(context: Context): BilleteraDatabase {
            // "?:" (operador Elvis): si instancia NO es null, la devuelve;
            // si es null, ejecuta lo de la derecha.
            return instancia ?: synchronized(this) {
                // synchronized = solo UN hilo a la vez puede entrar a este bloque.
                // Evita que dos hilos creen dos bases de datos al mismo tiempo.
                // Volvemos a preguntar por si otro hilo la creó mientras esperábamos.
                instancia ?: Room.databaseBuilder(
                    context.applicationContext,       // Contexto de TODA la app (no de una pantalla).
                    BilleteraDatabase::class.java,    // Qué clase de base de datos construir.
                    "billetera.db"                    // Nombre del archivo SQLite en el teléfono.
                )
                    // Si la versión instalada es más vieja que la del código, Room BORRA y recrea las
                    // tablas en lugar de "migrarlas". Lo aceptamos porque el historial real está en el
                    // SERVIDOR y se vuelve a descargar al iniciar sesión. En una app real se escribiría una Migration.
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    // "also" = haz algo más con el resultado: guardarlo en "instancia".
                    .also { instancia = it }
            }
        }
    }
}
