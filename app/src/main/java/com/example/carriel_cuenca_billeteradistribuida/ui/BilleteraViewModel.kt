// Paquete de la interfaz gráfica.
package com.example.carriel_cuenca_billeteradistribuida.ui

import androidx.lifecycle.ViewModel                            // Clase base de todo ViewModel.
import androidx.lifecycle.ViewModelProvider                    // Fábrica que crea ViewModels.
import androidx.lifecycle.viewModelScope                       // Ámbito de corrutinas del ViewModel.
import androidx.lifecycle.viewmodel.initializer                // Cómo construir el ViewModel.
import androidx.lifecycle.viewmodel.viewModelFactory           // DSL para declarar la fábrica.
import com.example.carriel_cuenca_billeteradistribuida.data.BilleteraRepository
import com.example.carriel_cuenca_billeteradistribuida.data.local.BilleteraDatabase
import com.example.carriel_cuenca_billeteradistribuida.model.CATEGORIA_SERVICIO_BASICO
import com.example.carriel_cuenca_billeteradistribuida.model.EstadoOperacion
import com.example.carriel_cuenca_billeteradistribuida.model.Movimiento
import com.example.carriel_cuenca_billeteradistribuida.model.TipoOperacion
import com.example.carriel_cuenca_billeteradistribuida.util.logHilo
import kotlinx.coroutines.CancellationException                // Señal de "corrutina cancelada".
import kotlinx.coroutines.flow.MutableStateFlow                // Estado que SE PUEDE cambiar.
import kotlinx.coroutines.flow.SharingStarted                  // Cuándo empezar/parar de escuchar un Flow.
import kotlinx.coroutines.flow.StateFlow                       // Estado de SOLO LECTURA.
import kotlinx.coroutines.flow.asStateFlow                     // Convierte Mutable -> solo lectura.
import kotlinx.coroutines.flow.stateIn                         // Convierte un Flow en StateFlow.
import kotlinx.coroutines.flow.update                          // Cambia un MutableStateFlow de forma segura.
import kotlinx.coroutines.launch                               // Lanza una corrutina.

// VIEWMODEL: guarda el ESTADO de la pantalla y recibe las acciones del usuario.
// - Sobrevive a la rotación de la pantalla (la Activity se destruye y se recrea; el ViewModel no).
// - No dibuja nada ni sabe de Compose: solo expone datos (StateFlow) y funciones.
// - No toca Room directamente: se lo pide al Repository.
class BilleteraViewModel(
    private val repositorio: BilleteraRepository   // Recibido desde afuera (lo crea la Factory de abajo).
) : ViewModel() {                                   // ":" = hereda de ViewModel.

    // ---------------- ESTADO QUE OBSERVA LA PANTALLA ----------------
    // Patrón "backing property": la versión MUTABLE es privada (_saldo) y
    // hacia afuera se expone una versión de SOLO LECTURA (saldo).
    // Así, solo el ViewModel puede cambiar el estado; la UI solo lo lee.

    // Historial: viene de Room a través del Repository.
    // stateIn convierte el Flow en un StateFlow que siempre tiene un "valor actual".
    // WhileSubscribed(5_000): deja de escuchar a Room 5 s después de que nadie mire
    // (ej. app en segundo plano), pero sobrevive a una rotación (que dura < 5 s).
    val historial: StateFlow<List<Movimiento>> = repositorio.observarHistorial()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Saldo: provisional (datos de prueba) hasta la Fase 4, cuando lo dará el servidor.
    private val _saldo = MutableStateFlow(DatosDePrueba.saldo)
    val saldo: StateFlow<Double> = _saldo.asStateFlow()

    // Estado del WebSocket: siempre false hasta la Fase 5.
    private val _enLinea = MutableStateFlow(false)
    val enLinea: StateFlow<Boolean> = _enLinea.asStateFlow()

    // Estado de la última operación (para mostrar mensajes de éxito o error).
    // El tipo se escribe explícito (<EstadoOperacion>) para poder asignarle cualquier opción de la familia.
    private val _estado = MutableStateFlow<EstadoOperacion>(EstadoOperacion.Inactivo)
    val estado: StateFlow<EstadoOperacion> = _estado.asStateFlow()

    // ---------------- ACCIONES DEL USUARIO ----------------

    // Se llama al tocar un botón. En esta fase guarda un movimiento de prueba de $10;
    // en la Fase 4 aquí se llamará al servidor por REST.
    fun ejecutarOperacion(tipo: TipoOperacion) {
        // Si ya hay una operación en curso, ignoramos el toque (evita dobles clics).
        // "is" pregunta "¿es de este tipo?".
        if (_estado.value is EstadoOperacion.Procesando) return

        // viewModelScope.launch: corrutina que corre en el hilo MAIN
        // y se cancela sola si el ViewModel se destruye (no hay fugas de memoria).
        viewModelScope.launch {
            logHilo("ViewModel: click en ${tipo.etiqueta}")                // main
            _estado.value = EstadoOperacion.Procesando

            val monto = 10.0
            val movimiento = Movimiento(
                tipo = tipo,
                monto = monto,
                fechaHora = System.currentTimeMillis(),
                detalle = "Prueba MVVM (Fase 3)",
                categoria = if (tipo == TipoOperacion.PAGO_SERVICIO) CATEGORIA_SERVICIO_BASICO else null
            )

            // try/catch: si algo falla, mostramos un error en vez de cerrar la app.
            try {
                // Esta llamada SUSPENDE la corrutina: el Repository salta a IO,
                // guarda y regresa. Mientras tanto, el hilo main sigue libre para la UI.
                val id = repositorio.guardarMovimiento(movimiento)
                logHilo("ViewModel: guardado id=$id, actualizando la UI")  // main otra vez

                // update { } cambia el valor de forma segura aunque varios hilos lo toquen.
                // "if" como expresión: suma si es ingreso, resta si es egreso.
                _saldo.update { actual -> if (tipo.esIngreso) actual + monto else actual - monto }
                _estado.value = EstadoOperacion.Exito("${tipo.etiqueta} de ${formatearMonto(monto)} guardado")
            } catch (e: CancellationException) {
                throw e   // Una cancelación NO es un error: se deja pasar (regla de las corrutinas).
            } catch (e: Exception) {
                logHilo("ViewModel: error ${e.message}")
                _estado.value = EstadoOperacion.Error("No se pudo guardar: ${e.message}")
            }
        }
    }

    // La pantalla avisa que ya mostró el mensaje; volvemos a Inactivo.
    fun mensajeMostrado() {
        _estado.value = EstadoOperacion.Inactivo
    }

    // ---------------- FÁBRICA (construcción manual, sin Hilt) ----------------
    // Android crea los ViewModels por nosotros (para que sobrevivan a la rotación),
    // pero no sabe que el nuestro necesita un Repository. La Factory se lo explica.
    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                // Obtenemos la Application. checkNotNull: si fuera null, falla con un mensaje claro.
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                // Armamos la cadena a mano: Base de datos -> DAO -> Repository -> ViewModel.
                val dao = BilleteraDatabase.obtener(app).movimientoDao()
                BilleteraViewModel(BilleteraRepository(dao))
            }
        }
    }
}
