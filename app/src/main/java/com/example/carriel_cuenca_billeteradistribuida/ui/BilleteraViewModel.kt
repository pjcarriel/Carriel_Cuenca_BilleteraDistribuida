// Paquete de la interfaz gráfica.
package com.example.carriel_cuenca_billeteradistribuida.ui

import androidx.lifecycle.ViewModel                            // Clase base de todo ViewModel.
import androidx.lifecycle.ViewModelProvider                    // Fábrica que crea ViewModels.
import androidx.lifecycle.viewModelScope                       // Ámbito de corrutinas del ViewModel.
import androidx.lifecycle.viewmodel.initializer                // Cómo construir el ViewModel.
import androidx.lifecycle.viewmodel.viewModelFactory           // DSL para declarar la fábrica.
import com.example.carriel_cuenca_billeteradistribuida.data.BilleteraRepository
import com.example.carriel_cuenca_billeteradistribuida.data.Resultado
import com.example.carriel_cuenca_billeteradistribuida.data.local.BilleteraDatabase
import com.example.carriel_cuenca_billeteradistribuida.data.remote.RetrofitClient
import com.example.carriel_cuenca_billeteradistribuida.model.EstadoOperacion
import com.example.carriel_cuenca_billeteradistribuida.model.Movimiento
import com.example.carriel_cuenca_billeteradistribuida.model.Solicitud
import com.example.carriel_cuenca_billeteradistribuida.util.logHilo
import kotlinx.coroutines.flow.MutableStateFlow                // Estado que SE PUEDE cambiar.
import kotlinx.coroutines.flow.SharingStarted                  // Cuándo empezar/parar de escuchar un Flow.
import kotlinx.coroutines.flow.StateFlow                       // Estado de SOLO LECTURA.
import kotlinx.coroutines.flow.asStateFlow                     // Convierte Mutable -> solo lectura.
import kotlinx.coroutines.flow.stateIn                         // Convierte un Flow en StateFlow.
import kotlinx.coroutines.launch                               // Lanza una corrutina.

// VIEWMODEL: guarda el ESTADO de la pantalla y recibe las acciones del usuario.
// - Sobrevive a la rotación de la pantalla.
// - No toca Room ni Retrofit directamente: se lo pide al Repository.
// - Todo lo que hace aquí corre en el hilo MAIN; el Repository salta a IO por su cuenta.
class BilleteraViewModel(
    private val repositorio: BilleteraRepository
) : ViewModel() {

    // ---------------- ESTADO QUE OBSERVA LA PANTALLA ----------------

    // Historial desde Room (sigue funcionando sin internet).
    val historial: StateFlow<List<Movimiento>> = repositorio.observarHistorial()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Saldo REAL: el último que respondió el servidor, guardado en Room.
    // "Double?" = null mientras todavía no se conoce (la tarjeta muestra "$ --").
    val saldo: StateFlow<Double?> = repositorio.observarSaldo()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Estado del WebSocket: siempre false hasta la Fase 5.
    private val _enLinea = MutableStateFlow(false)
    val enLinea: StateFlow<Boolean> = _enLinea.asStateFlow()

    // Estado de la última operación (Procesando muestra la barra de progreso; Exito/Error, un mensaje).
    private val _estado = MutableStateFlow<EstadoOperacion>(EstadoOperacion.Inactivo)
    val estado: StateFlow<EstadoOperacion> = _estado.asStateFlow()

    // ---------------- ACCIONES ----------------
    // Nota: el saldo NO se pide al crear el ViewModel, sino cuando la pantalla lo indica,
    // porque en Android 17 primero hay que obtener el permiso de red local (ver BilleteraApp).

    // El usuario negó el permiso de red local: la app funciona solo con los datos guardados.
    fun permisoRedLocalDenegado() {
        _estado.value = EstadoOperacion.Error("Sin permiso de red local: no se puede contactar al servidor")
    }

    // Pide el saldo al servidor. Si no hay conexión, se queda el último guardado en Room.
    fun sincronizarSaldo() {
        viewModelScope.launch {
            logHilo("ViewModel: sincronizando saldo con el servidor")
            // "when" sobre Resultado (sealed class): Exito o Error, no hay otra opción.
            when (val resultado = repositorio.sincronizarSaldo()) {
                is Resultado.Exito -> logHilo("ViewModel: saldo del servidor = ${resultado.datos}")
                is Resultado.Error -> _estado.value = EstadoOperacion.Error(resultado.mensaje)
            }
        }
    }

    // Se llama al CONFIRMAR un diálogo. Envía la operación al servidor por REST.
    fun ejecutarOperacion(solicitud: Solicitud) {
        // Si ya hay una operación en curso, ignoramos el toque (evita enviar dos veces).
        if (_estado.value is EstadoOperacion.Procesando) return

        // Corrutina en el hilo MAIN; se cancela sola si el ViewModel se destruye.
        viewModelScope.launch {
            logHilo("ViewModel: enviando ${solicitud.tipo.etiqueta} de ${solicitud.monto}")   // main
            _estado.value = EstadoOperacion.Procesando        // La UI muestra la barra de progreso.

            // Esta llamada SUSPENDE la corrutina: el Repository va a IO, llama a Retrofit,
            // guarda en Room y regresa. Mientras tanto el hilo main sigue dibujando (cero ANR).
            val resultado = repositorio.ejecutar(solicitud)

            logHilo("ViewModel: respuesta recibida, actualizando la UI")                     // main otra vez
            _estado.value = when (resultado) {
                // Éxito: mostramos el mensaje del servidor (ej. "Depósito realizado").
                is Resultado.Exito -> EstadoOperacion.Exito(resultado.datos.mensaje)
                // Error (409 saldo insuficiente, 404, sin conexión...): mensaje, SIN cerrar la app.
                is Resultado.Error -> EstadoOperacion.Error(resultado.mensaje)
            }
            // El saldo NO se cambia aquí: el Repository lo guardó en Room y el Flow "saldo" avisa solo.
        }
    }

    // La pantalla avisa que ya mostró el mensaje; volvemos a Inactivo.
    fun mensajeMostrado() {
        _estado.value = EstadoOperacion.Inactivo
    }

    // ---------------- FÁBRICA (construcción manual, sin Hilt) ----------------
    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                val db = BilleteraDatabase.obtener(app)
                // Armamos la cadena a mano: Room (2 DAOs) + Retrofit -> Repository -> ViewModel.
                val repositorio = BilleteraRepository(
                    movimientoDao = db.movimientoDao(),
                    cuentaDao = db.cuentaDao(),
                    api = RetrofitClient.api
                )
                BilleteraViewModel(repositorio)
            }
        }
    }
}
