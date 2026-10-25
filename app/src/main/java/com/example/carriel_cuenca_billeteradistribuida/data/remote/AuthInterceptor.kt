// =====================================================================
//   Es el "guardia" por donde pasa cada pedido antes de salir al servidor:
//   le pega el token para que el servidor sepa quién lo pide.
// =====================================================================

package com.example.carriel_cuenca_billeteradistribuida.data.remote

import okhttp3.Interceptor        // Interceptor = código que revisa cada pedido antes de enviarlo.
import okhttp3.Response

// Añade el token a todos los pedidos, así ninguna función tiene que ponerlo a mano.
class AuthInterceptor : Interceptor {
    // Si hay sesión, le pega el token al pedido; si no, lo deja igual. Luego lo deja seguir.
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val token = ProveedorToken.token
        // Con sesión: agregamos el token. Sin sesión (login o registro): va tal cual.
        val peticion = if (token != null) {
            original.newBuilder().header("Authorization", "Bearer $token").build()
        } else {
            original
        }
        return chain.proceed(peticion)       // El pedido sigue hacia el servidor.
    }
}
