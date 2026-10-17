// Paquete de la autenticación (login, registro y sesión).
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
import com.example.carriel_cuenca_billeteradistribuida.data.remote.RetrofitClient
import com.example.carriel_cuenca_billeteradistribuida.util.logHilo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Segundos ANTES del cierre en los que se muestra el aviso "Tu sesión se cerrará en...".
private const val SEGUNDOS_AVISO = 30

// Cada cuánto, como máximo, se avisa al servidor que el usuario sigue activo ("latido").
private const val INTERVALO_LATIDO_MS = 60_000L

// Mensaje para la pantalla de acceso: texto y si es un ERROR (rojo) o solo INFORMACIÓN (azul).
data class AvisoAcceso(val texto: String, val esError: Boolean)

// Estado de la sesión: decide QUÉ PANTALLA se muestra (sealed interface = familia cerrada).
sealed interface EstadoSesion {
    data object Cargando : EstadoSesion                          // Revisando si hay sesión guardada.
    data object SinSesion : EstadoSesion                         // Mostrar Login / Registro.
    // Sesión activa: mostrar la billetera. "saludo" = mensaje de bienvenida (o null).
    data class Activa(val sesion: SesionEntity, val saludo: String? = null) : EstadoSesion
}

// VIEWMODEL DE SESIÓN: login, registro, cierre y el TEMPORIZADOR DE INACTIVIDAD.
class SesionViewModel(private val repositorio: SesionRepository) : ViewModel() {

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

    // Momento del último toque del usuario y del último latido enviado al servidor.
    private var ultimaActividad = 0L
    private var ultimoLatido = 0L

    // Corrutina que vigila la inactividad. "Job?" = null si no hay sesión.
    private var vigilante: Job? = null

    init {
        // Al abrir la app: ¿hay una sesión guardada y todavía vigente?
        viewModelScope.launch {
            val guardada = repositorio.sesionGuardada()
            when {
                guardada == null -> _estado.value = EstadoSesion.SinSesion
                // Pasó más tiempo que el permitido desde el último toque (ej. la app estuvo cerrada).
                System.currentTimeMillis() - guardada.ultimaActividad > limiteMs(guardada) -> {
                    repositorio.cerrarSesion(avisarServidor = true)
                    _mensaje.value = AvisoAcceso("Tu sesión se cerró por inactividad", esError = false)
                    _estado.value = EstadoSesion.SinSesion
                }
                else -> activar(guardada, saludo = null)
            }
        }
    }

    // ---------------- ACCIONES DE LA PANTALLA DE ACCESO ----------------

    fun login(usuario: String, password: String) = acceder { repositorio.login(usuario, password) }

    fun registrar(nombre: String, usuario: String, password: String) =
        acceder(esRegistro = true) { repositorio.registrar(nombre, usuario, password) }

    fun mensajeVisto() {
        _mensaje.value = null
    }

    // Mensaje informativo (ej. si el usuario negó el permiso de red local).
    fun avisar(texto: String) {
        _mensaje.value = AvisoAcceso(texto, esError = true)
    }

    // Login y registro comparten la misma lógica: llamar, y si sale bien, activar la sesión.
    // "accion: suspend () -> Resultado<SesionEntity>" = lambda suspendible que hace la llamada.
    private fun acceder(esRegistro: Boolean = false, accion: suspend () -> Resultado<SesionEntity>) {
        if (_procesando.value) return
        viewModelScope.launch {
            _procesando.value = true
            _mensaje.value = null
            when (val resultado = accion()) {
                is Resultado.Exito -> {
                    val nombre = resultado.datos.nombre.substringBefore(" ")
                    val saludo = if (esRegistro) "¡Bienvenido/a, $nombre! Tu cuenta ${resultado.datos.cuenta} recibió $ 100.00 de regalo"
                    else "Hola de nuevo, $nombre"
                    activar(resultado.datos, saludo)
                }
                is Resultado.Error -> _mensaje.value = AvisoAcceso(resultado.mensaje, esError = true)
            }
            _procesando.value = false
        }
    }

    // ---------------- ACTIVIDAD E INACTIVIDAD ----------------

    // Se llama en CADA toque de pantalla (y al escribir en los diálogos). Reinicia el contador.
    fun registrarActividad() {
        if (_estado.value !is EstadoSesion.Activa) return
        val ahora = System.currentTimeMillis()
        ultimaActividad = ahora
        _avisoSegundos.value = null
        // Como mucho una vez por minuto: avisamos al servidor (latido) y guardamos en Room.
        // Así la sesión del SERVIDOR tampoco expira mientras el usuario usa la app.
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

    // El SERVIDOR rechazó la sesión (401 o cierre 1008 del WebSocket): volvemos al login.
    fun sesionInvalida(mensaje: String) = terminar(avisarServidor = false, mensaje = AvisoAcceso(mensaje, esError = true))

    // El saludo de bienvenida ya se mostró: lo quitamos para no repetirlo (ej. al rotar).
    fun saludoMostrado() {
        val actual = _estado.value
        if (actual is EstadoSesion.Activa && actual.saludo != null) _estado.value = actual.copy(saludo = null)
    }

    // ---------------- PRIVADAS ----------------

    private fun limiteMs(sesion: SesionEntity) = sesion.minutosInactividad * 60_000L

    // Abre la sesión en la app y arranca el vigilante de inactividad.
    private fun activar(sesion: SesionEntity, saludo: String?) {
        val ahora = System.currentTimeMillis()
        ultimaActividad = ahora
        ultimoLatido = ahora
        _estado.value = EstadoSesion.Activa(sesion, saludo)
        logHilo("SesionViewModel: sesión ACTIVA de ${sesion.usuario} (cuenta ${sesion.cuenta})")
        vigilante?.cancel()
        vigilante = viewModelScope.launch { vigilarInactividad(limiteMs(sesion)) }
    }

    // Cada segundo revisa cuánto falta. En los últimos 30 s muestra el aviso; al llegar a 0 cierra.
    // Es una corrutina en el hilo main: "delay" ESPERA sin bloquear (la pantalla sigue respondiendo).
    private suspend fun vigilarInactividad(limite: Long) {
        while (true) {
            delay(1_000)
            val restanteMs = limite - (System.currentTimeMillis() - ultimaActividad)
            when {
                restanteMs <= 0 -> {
                    logHilo("SesionViewModel: sesión cerrada por INACTIVIDAD")
                    terminar(avisarServidor = true, mensaje = AvisoAcceso("Tu sesión se cerró por inactividad (${limite / 60_000} min sin uso)", esError = false))
                    return
                }
                // "+ 999" redondea hacia arriba: 29 400 ms -> 30 s.
                restanteMs <= SEGUNDOS_AVISO * 1000L -> _avisoSegundos.value = ((restanteMs + 999) / 1000).toInt()
                else -> _avisoSegundos.value = null
            }
        }
    }

    // Cierra la sesión: vuelve a la pantalla de acceso y limpia la sesión local (y la del servidor).
    private fun terminar(avisarServidor: Boolean, mensaje: AvisoAcceso?) {
        if (_estado.value !is EstadoSesion.Activa) return     // Evita cerrar dos veces.
        vigilante?.cancel()
        vigilante = null
        _avisoSegundos.value = null
        _estado.value = EstadoSesion.SinSesion                 // La UI cambia al login de inmediato.
        _mensaje.value = mensaje
        viewModelScope.launch { repositorio.cerrarSesion(avisarServidor) }
    }

    // ---------------- FÁBRICA ----------------
    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                SesionViewModel(SesionRepository(RetrofitClient.api, BilleteraDatabase.obtener(app).sesionDao()))
            }
        }
    }
}
