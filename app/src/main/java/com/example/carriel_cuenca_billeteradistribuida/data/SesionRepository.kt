// =====================================================================
//   Se encarga de ENTRAR y SALIR de la app: iniciar sesión, registrarse,
//   mantener viva la sesión y cerrarla.
// =====================================================================

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

// Repositorio de la sesión. Todo lo de internet o disco va en segundo plano (Dispatchers.IO).
class SesionRepository(
    private val sesionDao: SesionDao,
    private val contexto: Context                   // Contexto de la app (para leer la dirección del servidor).
) {
    // Conexión con el servidor (se pide cada vez, por si cambió la dirección).
    private val api: BilleteraApi get() = RetrofitClient.api

    // Al abrir la app: carga la dirección del servidor y busca una sesión guardada.
    // Si la hay, el usuario entra directo sin escribir la contraseña.
    suspend fun sesionGuardada(): SesionEntity? = withContext(Dispatchers.IO) {
        ConfigServidor.cargar(contexto)
        logHilo("SesionRepository: servidor configurado en ${ConfigServidor.host()}")
        // Si hay sesión, deja listo su token y la devuelve.
        sesionDao.obtener()?.also { ProveedorToken.token = it.token }
    }

    // Inicio de sesión: revisa usuario y contraseña con el servidor y guarda la sesión.
    suspend fun login(usuario: String, password: String): Resultado<SesionEntity> = withContext(Dispatchers.IO) {
        llamarServidor {
            logHilo("SesionRepository: POST /auth/login ($usuario)")
            guardarSesion(api.login(LoginRequest(usuario, password)))
        }
    }

    // Registro: crea el usuario con su cuenta y $100, y lo deja dentro de la app.
    suspend fun registrar(nombre: String, usuario: String, password: String): Resultado<SesionEntity> =
        withContext(Dispatchers.IO) {
            llamarServidor {
                logHilo("SesionRepository: POST /auth/registro ($usuario)")
                guardarSesion(api.registrar(RegistroRequest(usuario, nombre, password)))
            }
        }

    // "Latido": le dice al servidor "sigo aquí" para que la sesión no expire.
    suspend fun latido(): Resultado<Unit> = withContext(Dispatchers.IO) {
        llamarServidor {
            logHilo("SesionRepository: latido de actividad (GET /mi-cuenta)")
            api.miCuenta()
            Unit
        }
    }

    // Anota la hora de la última actividad del usuario.
    suspend fun guardarActividad(momento: Long) = withContext(Dispatchers.IO) {
        sesionDao.actualizarActividad(momento)
    }

    // Cerrar sesión: avisa al servidor (si se puede) y borra la sesión del teléfono.
    suspend fun cerrarSesion(avisarServidor: Boolean) = withContext(Dispatchers.IO) {
        if (avisarServidor) {
            // Si no hay internet, igual se cierra la sesión en el teléfono.
            runCatching { api.logout() }.onFailure { logHilo("SesionRepository: no se pudo avisar el logout al servidor") }
        }
        sesionDao.borrar()
        ProveedorToken.token = null
        logHilo("SesionRepository: sesión local cerrada")
    }

    // Guarda en el teléfono la sesión que respondió el servidor y activa su token.
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
