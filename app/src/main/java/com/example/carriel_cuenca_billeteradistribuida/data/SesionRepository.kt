// Paquete "data".
package com.example.carriel_cuenca_billeteradistribuida.data

import android.content.Context
import com.example.carriel_cuenca_billeteradistribuida.data.local.SesionDao
import com.example.carriel_cuenca_billeteradistribuida.data.local.SesionEntity
import com.example.carriel_cuenca_billeteradistribuida.data.remote.BilleteraApi
import com.example.carriel_cuenca_billeteradistribuida.data.remote.ConfigServidor
import com.example.carriel_cuenca_billeteradistribuida.data.remote.RetrofitClient
import com.example.carriel_cuenca_billeteradistribuida.data.remote.ProveedorToken
import com.example.carriel_cuenca_billeteradistribuida.data.remote.dto.LoginRequest
import com.example.carriel_cuenca_billeteradistribuida.data.remote.dto.RegistroRequest
import com.example.carriel_cuenca_billeteradistribuida.data.remote.dto.SesionDto
import com.example.carriel_cuenca_billeteradistribuida.util.logHilo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// REPOSITORIO DE SESIÓN: login, registro, logout y la sesión guardada en Room.
// Igual que el otro repositorio: TODO acceso a red o disco ocurre en Dispatchers.IO.
class SesionRepository(
    private val sesionDao: SesionDao,
    private val contexto: Context                   // Contexto de la APP (para leer la dirección del servidor).
) {
    // Se lee en CADA uso (no se guarda fijo): así, si cambia la dirección del servidor,
    // la próxima llamada usa la dirección nueva. (Antes se capturaba una sola vez y quedaba en 10.0.2.2.)
    private val api: BilleteraApi get() = RetrofitClient.api

    // Sesión guardada en el teléfono (al abrir la app). Si existe, deja listo el token en memoria.
    // Es lo PRIMERO que hace la app: aquí también se lee la dirección del servidor (disco -> IO).
    suspend fun sesionGuardada(): SesionEntity? = withContext(Dispatchers.IO) {
        ConfigServidor.cargar(contexto)
        logHilo("SesionRepository: servidor configurado en ${ConfigServidor.host()}")
        // "?.also { }" = solo si hay sesión (no null) copia su token a memoria; devuelve la misma sesión.
        sesionDao.obtener()?.also { ProveedorToken.token = it.token }
    }

    // Inicia sesión en el servidor y guarda la sesión en Room.
    suspend fun login(usuario: String, password: String): Resultado<SesionEntity> = withContext(Dispatchers.IO) {
        llamarServidor {
            logHilo("SesionRepository: POST /auth/login ($usuario)")
            guardarSesion(api.login(LoginRequest(usuario, password)))
        }
    }

    // Crea un usuario nuevo (con su cuenta y $100) y deja la sesión abierta.
    suspend fun registrar(nombre: String, usuario: String, password: String): Resultado<SesionEntity> =
        withContext(Dispatchers.IO) {
            llamarServidor {
                logHilo("SesionRepository: POST /auth/registro ($usuario)")
                guardarSesion(api.registrar(RegistroRequest(usuario, nombre, password)))
            }
        }

    // "LATIDO": petición liviana que avisa al servidor que el usuario sigue activo,
    // para que su sesión no expire mientras usa la app sin hacer operaciones.
    suspend fun latido(): Resultado<Unit> = withContext(Dispatchers.IO) {
        llamarServidor {
            logHilo("SesionRepository: latido de actividad (GET /mi-cuenta)")
            api.miCuenta()
            Unit
        }
    }

    // Guarda en Room el momento de la última actividad (por si la app se cierra y se vuelve a abrir).
    suspend fun guardarActividad(momento: Long) = withContext(Dispatchers.IO) {
        sesionDao.actualizarActividad(momento)
    }

    // Cierra la sesión: avisa al servidor (si se puede) y borra la sesión local.
    // "avisarServidor = false" cuando el servidor ya la invalidó (401) y no tiene sentido llamarlo.
    suspend fun cerrarSesion(avisarServidor: Boolean) = withContext(Dispatchers.IO) {
        if (avisarServidor) {
            // runCatching: si no hay conexión, igual cerramos la sesión local.
            runCatching { api.logout() }.onFailure { logHilo("SesionRepository: no se pudo avisar el logout al servidor") }
        }
        sesionDao.borrar()
        ProveedorToken.token = null
        logHilo("SesionRepository: sesión local cerrada")
    }

    // Convierte la respuesta del servidor en la sesión local, la guarda y activa el token.
    // Desde que ProveedorToken.token tiene valor, AuthInterceptor lo agrega a TODAS las peticiones.
    private suspend fun guardarSesion(dto: SesionDto): SesionEntity {
        val sesion = SesionEntity(
            token = dto.token,
            usuario = dto.usuario,
            nombre = dto.nombre,
            cuenta = dto.cuenta,
            minutosInactividad = dto.minutosInactividad,
            ultimaActividad = System.currentTimeMillis()
        )
        sesionDao.guardar(sesion)
        ProveedorToken.token = sesion.token
        return sesion
    }
}
