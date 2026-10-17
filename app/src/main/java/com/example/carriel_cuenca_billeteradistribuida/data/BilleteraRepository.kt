// Paquete "data": todo lo relacionado con OBTENER y GUARDAR datos.
package com.example.carriel_cuenca_billeteradistribuida.data

import com.example.carriel_cuenca_billeteradistribuida.data.local.MovimientoDao
import com.example.carriel_cuenca_billeteradistribuida.data.local.aEntidad
import com.example.carriel_cuenca_billeteradistribuida.data.local.aModelo
import com.example.carriel_cuenca_billeteradistribuida.model.Movimiento
import com.example.carriel_cuenca_billeteradistribuida.util.logHilo
import kotlinx.coroutines.Dispatchers          // Dispatchers.IO = hilos para disco y red.
import kotlinx.coroutines.flow.Flow            // Flujo de datos que emite valores con el tiempo.
import kotlinx.coroutines.flow.flowOn          // Cambia el hilo donde se PRODUCE el Flow.
import kotlinx.coroutines.flow.map             // Transforma cada valor del Flow.
import kotlinx.coroutines.withContext          // Ejecuta un bloque en otro hilo y vuelve.

// REPOSITORIO: el ÚNICO lugar de la app que sabe DE DÓNDE vienen los datos.
// El ViewModel le pide "dame el historial" o "guarda esto" sin saber si es Room,
// el servidor (Fase 4) o el WebSocket (Fase 5).
// REGLA DE ESTE ARCHIVO: todo trabajo de disco/red se hace en Dispatchers.IO.
//
// "private val dao" en el constructor: el repositorio RECIBE el DAO desde afuera
// (construcción manual, sin Hilt). "private" = nadie más puede usarlo desde aquí.
class BilleteraRepository(private val dao: MovimientoDao) {

    // Devuelve el historial como un Flow que se actualiza solo.
    // Se escribe con "=" porque la función es una sola expresión.
    fun observarHistorial(): Flow<List<Movimiento>> =
        dao.observarTodos()                                   // 1. Room emite la lista cuando la tabla cambia.
            .map { entidades ->                               // 2. Convertimos filas (Entity) en modelos.
                logHilo("Repository: leyendo historial de Room (${entidades.size} filas)")
                entidades.map { it.aModelo() }                // "it" = cada entidad de la lista.
            }
            .flowOn(Dispatchers.IO)                           // 3. Todo lo de ARRIBA corre en IO.

    // Guarda un movimiento en Room y devuelve el id que se le asignó.
    // "suspend": quien la llama se pausa (sin bloquear su hilo) hasta que termine.
    suspend fun guardarMovimiento(movimiento: Movimiento): Long =
        withContext(Dispatchers.IO) {                         // Saltamos al hilo IO...
            logHilo("Repository: insertando ${movimiento.tipo.etiqueta} en Room")
            dao.insertar(movimiento.aEntidad())               // ...escribimos en disco...
        }                                                     // ...y al salir, volvemos al hilo de quien llamó.
}
