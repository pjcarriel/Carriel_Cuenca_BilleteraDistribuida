// =====================================================================
//   El "cerebro" de la sesión: decide si se ve el Login o la billetera,
//   hace el login y el registro, y cierra la sesión si el usuario no usa la app.
// =====================================================================

package com.example.carriel_cuenca_billeteradistribuida.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.carriel_cuenca_billeteradistribuida.data.HTTP_NO_AUTORIZADO
import com.example.carriel_cuenca_billeteradistribuida.data.Resultado
import com.example.carriel_cuenca_billeteradistribuida.data.SesionRepository
import com.example.carriel_cuenca_billeteradistribuida.data.local.BilleteraDatabase
import com.example.carriel_cuenca_billeteradistribuida.data.local.SesionEntity
import com.example.carriel_cuenca_billeteradistribuida.data.remote.ConfigServidor
import com.example.carriel_cuenca_billeteradistribuida.util.logHilo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Segundos ANTES del cierre en los que se muestra el aviso "Tu sesión se cerrará en...".
private const val SEGUNDOS_AVISO = 30

// Cada cuánto, como máximo, se avisa al servidor que el usuario sigue activo.
// A este aviso lo llamamos "latido" (como el latido del corazón: "sigo aquí").
private const val INTERVALO_LATIDO_MS = 60_000L

// Mensaje para la pantalla de acceso: texto y si es un ERROR (rojo) o solo INFORMACIÓN (azul).
data class AvisoAcceso(val texto: String, val esError: Boolean)

// Las 3 situaciones posibles de la sesión. Según cuál sea, se muestra una pantalla u otra.
sealed interface EstadoSesion {
    data object Cargando : EstadoSesion                          // Revisando si hay sesión guardada.
    data object SinSesion : EstadoSesion                         // Mostrar Login / Registro.
    // Sesión activa: mostrar la billetera. "saludo" = mensaje de bienvenida (o null).
    data class Activa(val sesion: SesionEntity, val saludo: String? = null) : EstadoSesion
}

// VIEWMODEL DE SESIÓN: login, registro, cierre de sesión y el reloj de inactividad.
// (Un ViewModel guarda los datos de la pantalla y la lógica, separados del dibujo.)
class SesionViewModel(private val repositorio: SesionRepository) : ViewModel() {

    // Estado de la sesión ("_estado" se cambia aquí; "estado" lo lee la pantalla).
    private val _estado = MutableStateFlow<EstadoSesion>(EstadoSesion.Cargando)
    val estado: StateFlow<EstadoSesion> = _estado.asStateFlow()

    // Segundos que faltan para el cierre automático (solo en los últimos 30 s); null = sin aviso.
    private val _avisoSegundos = MutableStateFlow<Int?>(null)
    val avisoSegundos: StateFlow<Int?> = _avisoSegundos.asStateFlow()

    // Mensaje para la pantalla de acceso (error de login, "sesión cerrada por inactividad"...).
    private val _mensaje = MutableStateFlow<AvisoAcceso?>(null)
    val mensaje: StateFlow<AvisoAcceso?> = _mensaje.asStateFlow()

    // true mientras se espera la respuesta de login/registro (deshabilita el botón).
    private val _procesando = MutableStateFlow(false)
    val procesando: StateFlow<Boolean> = _procesando.asStateFlow()

    // Hora del último toque del usuario y del último latido enviado al servidor.
    // La pantalla no los muestra; solo los usa el vigilante de inactividad.
    private var ultimaActividad = 0L
    private var ultimoLatido = 0L

    // Tarea en segundo plano que vigila la inactividad (null si no hay sesión).
    private var vigilante: Job? = null

    init {
        // Al abrir la app: ¿hay una sesión guardada en el teléfono y todavía vale?
        // Mientras se revisa, la pantalla muestra "Cargando" sin congelarse.
        viewModelScope.launch {
            val guardada = repositorio.sesionGuardada()
            when {
                guardada == null -> _estado.value = EstadoSesion.SinSesion
                // Pasó más tiempo que el permitido desde el último toque (ej. la app estuvo cerrada):
                // le preguntamos al SERVIDOR, que es quien decide si la sesión sigue valiendo.
                System.currentTimeMillis() - guardada.ultimaActividad > limiteMs(guardada) ->
                    when (val r = repositorio.latido()) {
                        is Resultado.Exito -> activar(guardada, saludo = null)
                        // Sin conexión: se abre la billetera con los datos guardados en el teléfono.
                        // Si el servidor dijo que la sesión venció, se cierra y se avisa.
                        is Resultado.Error -> if (r.codigoHttp == null) {
                            logHilo("SesionViewModel: servidor inalcanzable, abriendo en modo sin conexión")
                            activar(guardada, saludo = null)
                        } else {
                            repositorio.cerrarSesion(avisarServidor = false)
                            _mensaje.value = AvisoAcceso("Tu sesión se cerró por inactividad", esError = false)
                            _estado.value = EstadoSesion.SinSesion
                        }
                    }
                else -> activar(guardada, saludo = null)
            }
        }
    }

    // ---------------- ACCIONES DE LA PANTALLA DE ACCESO ----------------

    // Inicia sesión con usuario y contraseña.
    fun login(usuario: String, password: String) = acceder { repositorio.login(usuario, password) }

    // Crea una cuenta nueva y entra directamente.
    fun registrar(nombre: String, usuario: String, password: String) =
        acceder(esRegistro = true) { repositorio.registrar(nombre, usuario, password) }

    // La pantalla ya mostró el mensaje: lo borramos para no repetirlo.
    fun mensajeVisto() {
        _mensaje.value = null
    }

    // Muestra un mensaje en la pantalla de acceso (ej. si el usuario negó el permiso de red local).
    fun avisar(texto: String) {
        _mensaje.value = AvisoAcceso(texto, esError = true)
    }

    // Pasos comunes de login y registro: llamar al servidor y, si sale bien, abrir la sesión.
    // "accion" es la llamada concreta (login o registro) que se va a hacer.
    private fun acceder(esRegistro: Boolean = false, accion: suspend () -> Resultado<SesionEntity>) {
        if (_procesando.value) return                          // Si ya se está enviando, no se repite.
        viewModelScope.launch {
            _procesando.value = true
            _mensaje.value = null
            when (val resultado = accion()) {
                is Resultado.Exito -> {
                    // Se toma solo el primer nombre ("Pamela Carriel" -> "Pamela") para el saludo.
                    val nombre = resultado.datos.nombre.substringBefore(" ")
                    val saludo = if (esRegistro) "¡Bienvenido/a, $nombre! Tu cuenta ${resultado.datos.cuenta} recibió $ 100.00 de regalo"
                    else "Hola de nuevo, $nombre"
                    activar(resultado.datos, saludo)
                }
                is Resultado.Error -> {
                    // Si no se llegó al servidor, se muestra a qué dirección se intentó conectar,
                    // así se detecta enseguida una IP mal escrita.
                    val texto = if (resultado.codigoHttp == null)
                        "No se pudo conectar con el servidor (${ConfigServidor.host()}). Revisa la dirección y tu conexión."
                    else resultado.mensaje
                    _mensaje.value = AvisoAcceso(texto, esError = true)
                }
            }
            _procesando.value = false                          // Se vuelve a habilitar el botón.
        }
    }

    // ---------------- ACTIVIDAD E INACTIVIDAD ----------------

    // Se llama cada vez que el usuario toca la pantalla o escribe. Reinicia el reloj de inactividad.
    fun registrarActividad() {
        if (_estado.value !is EstadoSesion.Activa) return
        val ahora = System.currentTimeMillis()
        ultimaActividad = ahora
        _avisoSegundos.value = null
        // Una vez por minuto como máximo: avisa al servidor que el usuario sigue activo.
        if (ahora - ultimoLatido > INTERVALO_LATIDO_MS) {
            ultimoLatido = ahora
            viewModelScope.launch {
                repositorio.guardarActividad(ahora)
                val latido = repositorio.latido()
                if (latido is Resultado.Error && latido.codigoHttp == HTTP_NO_AUTORIZADO) sesionInvalida(latido.mensaje)
            }
        }
    }

    // El usuario pulsó "Cerrar sesión".
    fun cerrarSesion() = terminar(avisarServidor = true, mensaje = AvisoAcceso("Cerraste sesión correctamente", esError = false))

    // El servidor dijo que la sesión ya no es válida: volvemos al login con un mensaje.
    fun sesionInvalida(mensaje: String) = terminar(avisarServidor = false, mensaje = AvisoAcceso(mensaje, esError = true))

    // El saludo de bienvenida ya se mostró: lo quitamos para no repetirlo (ej. al girar el teléfono).
    fun saludoMostrado() {
        val actual = _estado.value
        // Se deja todo igual, pero sin el saludo.
        if (actual is EstadoSesion.Activa && actual.saludo != null) _estado.value = actual.copy(saludo = null)
    }

    // ---------------- FUNCIONES INTERNAS ----------------

    // Tiempo máximo sin uso antes de cerrar la sesión (los minutos pasados a milisegundos).
    private fun limiteMs(sesion: SesionEntity) = sesion.minutosInactividad * 60_000L

    // Abre la sesión en la app y arranca el vigilante de inactividad.
    private fun activar(sesion: SesionEntity, saludo: String?) {
        val ahora = System.currentTimeMillis()
        ultimaActividad = ahora
        ultimoLatido = ahora
        _estado.value = EstadoSesion.Activa(sesion, saludo)
        logHilo("SesionViewModel: sesión ACTIVA de ${sesion.usuario} (cuenta ${sesion.cuenta})")
        // Cancelamos un vigilante anterior (si lo hubiera) para no tener DOS contando a la vez.
        vigilante?.cancel()
        vigilante = viewModelScope.launch { vigilarInactividad(limiteMs(sesion)) }
    }

    // VIGILANTE DE INACTIVIDAD: cada segundo revisa cuánto falta.
    // En los últimos 30 s muestra el aviso; al llegar a 0, cierra la sesión.
    private suspend fun vigilarInactividad(limite: Long) {
        // Se repite hasta que se cierra la sesión o hasta que se detiene el vigilante.
        while (true) {
            delay(1_000)
            val restanteMs = limite - (System.currentTimeMillis() - ultimaActividad)
            when {
                restanteMs <= 0 -> {
                    logHilo("SesionViewModel: sesión cerrada por INACTIVIDAD")
                    terminar(avisarServidor = true, mensaje = AvisoAcceso("Tu sesión se cerró por inactividad (${limite / 60_000} min sin uso)", esError = false))
                    return
                }
                // Segundos que faltan, redondeados hacia arriba (29,4 s -> 30 s).
                restanteMs <= SEGUNDOS_AVISO * 1000L -> _avisoSegundos.value = ((restanteMs + 999) / 1000).toInt()
                else -> _avisoSegundos.value = null
            }
        }
    }

    // Cierra la sesión: vuelve a la pantalla de acceso y borra la sesión del teléfono (y del servidor).
    private fun terminar(avisarServidor: Boolean, mensaje: AvisoAcceso?) {
        if (_estado.value !is EstadoSesion.Activa) return     // Evita cerrar dos veces.
        vigilante?.cancel()
        vigilante = null
        _avisoSegundos.value = null
        _estado.value = EstadoSesion.SinSesion                 // La UI cambia al login de inmediato.
        _mensaje.value = mensaje
        // En segundo plano: avisa al servidor y borra la sesión guardada en el teléfono.
        viewModelScope.launch { repositorio.cerrarSesion(avisarServidor) }
    }

    // ---------------- FÁBRICA ----------------
    // Le dice a Android cómo crear este ViewModel.
    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                SesionViewModel(SesionRepository(BilleteraDatabase.obtener(app).sesionDao(), app.applicationContext))
            }
        }
    }
}
