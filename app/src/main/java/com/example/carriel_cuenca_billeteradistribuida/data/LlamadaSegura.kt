// Paquete "data".
package com.example.carriel_cuenca_billeteradistribuida.data

import com.example.carriel_cuenca_billeteradistribuida.data.remote.dto.ErrorRespuestaDto
import com.example.carriel_cuenca_billeteradistribuida.util.logHilo
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException                 // Error HTTP (400, 401, 404, 409, 500...).
import java.io.IOException                     // Error de red (servidor apagado, sin internet).

// Código HTTP de "no autenticado / sesión expirada".
const val HTTP_NO_AUTORIZADO = 401

private val gson = Gson()

// Ejecuta un bloque que habla con el servidor y convierte CUALQUIER fallo en Resultado.Error.
// La comparten SesionRepository y BilleteraRepository (antes estaba dentro de uno solo).
// "internal" = visible en todo el módulo de la app.
// "bloque: suspend () -> T" = recibe una LAMBDA suspend que devuelve un T (puede llamar a Retrofit).
// El ORDEN de los catch importa: se prueba de arriba hacia abajo y Exception (el más general) va al final.
internal suspend fun <T> llamarServidor(bloque: suspend () -> T): Resultado<T> =
    try {
        Resultado.Exito(bloque())
    } catch (e: CancellationException) {
        throw e                                          // Cancelar no es un error: se deja pasar.
    } catch (e: HttpException) {
        val mensaje = mensajeDeError(e)
        logHilo("Servidor respondió HTTP ${e.code()} -> $mensaje")
        Resultado.Error(mensaje, e.code())
    } catch (e: IOException) {
        logHilo("Sin conexión con el servidor (${e.javaClass.simpleName})")
        Resultado.Error("Sin conexión con el servidor. Se muestran los últimos datos guardados.")
    } catch (e: Exception) {
        // Red de seguridad: respuesta con JSON inesperado, dirección mal escrita, etc.
        // Nunca dejamos que un fallo así cierre la app.
        logHilo("Fallo inesperado al hablar con el servidor (${e.javaClass.simpleName}: ${e.message})")
        Resultado.Error("Respuesta inesperada del servidor. Revisa la dirección del servidor.")
    }

// Lee el JSON de error del servidor ({"codigo":..., "mensaje":...}) para mostrar su mensaje.
// Si el cuerpo no trae ese JSON, "?:" usa un mensaje genérico según el código HTTP.
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
