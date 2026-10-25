// =====================================================================
//   El "casco de seguridad" de las llamadas al servidor: si algo falla,
//   devuelve un mensaje claro en vez de cerrar la app.
// =====================================================================

package com.example.carriel_cuenca_billeteradistribuida.data

import com.example.carriel_cuenca_billeteradistribuida.data.remote.dto.ErrorRespuestaDto
import com.example.carriel_cuenca_billeteradistribuida.util.logHilo
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException                 // El servidor respondió con error (400, 401, 404, 409...).
import java.io.IOException                     // No se pudo llegar al servidor (apagado, sin internet).

// Código 401: la sesión no es válida o venció; la app manda al login.
const val HTTP_NO_AUTORIZADO = 401

// Lee el texto (JSON) que manda el servidor.
private val gson = Gson()

// Hace una llamada al servidor y siempre devuelve un Resultado (Exito o Error).
// La usan SesionRepository y BilleteraRepository.
internal suspend fun <T> llamarServidor(bloque: suspend () -> T): Resultado<T> =
    try {
        Resultado.Exito(bloque())
    } catch (e: CancellationException) {
        throw e                                          // Se canceló la tarea: no es un error.
    } catch (e: HttpException) {
        // El servidor respondió con error: se usa su mensaje.
        val mensaje = mensajeDeError(e)
        logHilo("Servidor respondió HTTP ${e.code()} -> $mensaje")
        Resultado.Error(mensaje, e.code())
    } catch (e: IOException) {
        // No se pudo llegar al servidor.
        logHilo("Sin conexión con el servidor (${e.javaClass.simpleName})")
        Resultado.Error("Sin conexión con el servidor. Se muestran los últimos datos guardados.")
    } catch (e: Exception) {
        // Cualquier otro problema raro: tampoco cierra la app.
        logHilo("Fallo inesperado al hablar con el servidor (${e.javaClass.simpleName}: ${e.message})")
        Resultado.Error("Respuesta inesperada del servidor. Revisa la dirección del servidor.")
    }

// Saca el mensaje para el usuario: el que manda el servidor o, si no hay, uno según el código.
private fun mensajeDeError(e: HttpException): String {
    val cuerpo = e.response()?.errorBody()?.string()
    val error = runCatching { gson.fromJson(cuerpo, ErrorRespuestaDto::class.java) }.getOrNull()
    return error?.mensaje ?: when (e.code()) {
        HTTP_NO_AUTORIZADO -> "Tu sesión no es válida. Inicia sesión de nuevo"
        409 -> "Saldo insuficiente"
        404 -> "No existe"
        400 -> "Datos inválidos"
        else -> "Error del servidor (${e.code()})"
    }
}
