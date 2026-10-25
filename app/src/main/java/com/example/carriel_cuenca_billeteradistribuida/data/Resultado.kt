// =====================================================================
//   La respuesta del servidor: o salió BIEN (con datos) o salió MAL
//   (con un mensaje). Así la app nunca se cierra por un error.
// =====================================================================

package com.example.carriel_cuenca_billeteradistribuida.data

// Respuesta de una llamada al servidor. "T" = cualquier tipo de dato (saldo, factura, lista...).
sealed class Resultado<out T> {

    // Salió bien: trae los datos que pidió la app.
    data class Exito<T>(val datos: T) : Resultado<T>()

    // Salió mal: trae el mensaje para el usuario y, si hubo, el código de error (400, 404...).
    data class Error(val mensaje: String, val codigoHttp: Int? = null) : Resultado<Nothing>()
}
