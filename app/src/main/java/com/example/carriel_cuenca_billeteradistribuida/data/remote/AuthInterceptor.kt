// Paquete de comunicación con el servidor.
package com.example.carriel_cuenca_billeteradistribuida.data.remote

import okhttp3.Interceptor        // "Interceptor": código que se ejecuta en CADA petición antes de enviarla.
import okhttp3.Response

// INTERCEPTOR DE AUTENTICACIÓN: añade "Authorization: Bearer <token>" a todas las peticiones.
// Así ninguna función de BilleteraApi tiene que recibir el token como parámetro.
// Corre en el hilo de OkHttp (nunca en main).
class AuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        // Leemos el token UNA vez ("val") para usar el mismo valor aunque otro hilo lo cambie.
        val token = ProveedorToken.token
        // Si hay sesión, copiamos la petición con la cabecera extra; si no, va tal cual (login/registro).
        val peticion = if (token != null) {
            original.newBuilder().header("Authorization", "Bearer $token").build()
        } else {
            original
        }
        return chain.proceed(peticion)       // Continúa hacia el servidor (o el siguiente interceptor).
    }
}
