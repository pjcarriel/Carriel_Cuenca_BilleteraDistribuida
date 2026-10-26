// =====================================================================
//   Prepara UNA sola vez la conexión con el servidor y la comparte con
//   toda la app, para que los pedidos de BilleteraApi viajen por internet.
// =====================================================================

package com.example.carriel_cuenca_billeteradistribuida.data.remote

import android.util.Log                               // Para escribir mensajes en el Logcat.
import okhttp3.OkHttpClient                           // El que envía los pedidos por internet.
import okhttp3.logging.HttpLoggingInterceptor         // Muestra cada pedido y respuesta en el Logcat.
import retrofit2.Retrofit                             // Arma la API para hablar con el servidor.
import retrofit2.converter.gson.GsonConverterFactory  // Traduce entre JSON y objetos Kotlin.
import java.util.concurrent.TimeUnit                  // Unidades de tiempo (segundos).

// Conexión y API únicas para toda la app.
object RetrofitClient {

    // Anota en el Logcat cada pedido y respuesta, ocultando el token.
    private val interceptorLog = HttpLoggingInterceptor { mensaje ->
        Log.d("HTTP", "[${Thread.currentThread().name}] $mensaje")
    }.apply {
        level = HttpLoggingInterceptor.Level.BODY
        redactHeader("Authorization")
    }

    // Conexión compartida (también la usa el WebSocket).
    val clienteHttp: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor())          // 1. Pega el token al pedido.
        .addInterceptor(interceptorLog)             // 2. Lo anota en el Logcat.
        // Tiempos largos porque el servidor gratis de Render tarda ~50 s en "despertar".
        .connectTimeout(15, TimeUnit.SECONDS)       // Si en 15 s no conecta -> error.
        .readTimeout(70, TimeUnit.SECONDS)          // Si en 70 s no responde -> error.
        .build()

    // Guarda la API ya armada junto con la dirección del servidor que usó.
    @Volatile
    private var cache: Pair<String, BilleteraApi>? = null

    // Entrega la API lista para usar; si cambió la dirección del servidor, arma una nueva.
    val api: BilleteraApi
        get() {
            val base = ConfigServidor.urlBase()
            cache?.let { if (it.first == base) return it.second }    // Misma dirección: reutilizamos.
            val nueva = Retrofit.Builder()
                .baseUrl(base)
                .client(clienteHttp)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(BilleteraApi::class.java)
            cache = base to nueva
            return nueva
        }
}
