// Paquete "data".
package com.example.carriel_cuenca_billeteradistribuida.data

// Resultado de una llamada al servidor: o salió BIEN (con datos) o salió MAL (con mensaje).
// En vez de lanzar excepciones hasta la pantalla, el Repository devuelve un Resultado
// y el ViewModel decide con un "when" qué mostrar. Así la app NUNCA se cierra por un error HTTP.
//
// "<out T>" = GENÉRICO: T es el tipo de dato del éxito (Double, OperacionRespuestaDto, ...).
// "out" permite usar un Resultado<Nothing> (el Error) donde se espera cualquier Resultado<T>.
sealed class Resultado<out T> {

    // Éxito: lleva los datos que devolvió el servidor.
    data class Exito<T>(val datos: T) : Resultado<T>()

    // Error: mensaje para el usuario y, si hubo respuesta HTTP, su código (400, 404, 409...).
    // "Nothing" = "no hay datos"; por eso Error sirve para cualquier tipo T.
    data class Error(val mensaje: String, val codigoHttp: Int? = null) : Resultado<Nothing>()
}
