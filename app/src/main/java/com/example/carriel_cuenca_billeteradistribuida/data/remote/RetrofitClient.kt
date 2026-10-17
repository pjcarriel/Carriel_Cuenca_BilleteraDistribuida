// Paquete de comunicación con el servidor.
package com.example.carriel_cuenca_billeteradistribuida.data.remote

import android.util.Log                               // Escribir en el Logcat.
import okhttp3.OkHttpClient                           // Cliente HTTP (también servirá para el WebSocket).
import okhttp3.logging.HttpLoggingInterceptor         // Registra cada petición/respuesta HTTP.
import retrofit2.Retrofit                             // Constructor de la API REST.
import retrofit2.converter.gson.GsonConverterFactory  // Conversor JSON <-> objetos con Gson.
import java.util.concurrent.TimeUnit                  // Unidades de tiempo (segundos).

// Crea UNA sola vez el cliente HTTP y la API (object = instancia única para toda la app).
object RetrofitClient {

    // Interceptor de LOG: muestra en el Logcat (tag "HTTP") cada petición y respuesta con el NOMBRE DEL HILO.
    // redactHeader: el token NUNCA se escribe en el Logcat (aparece como "██").
    private val interceptorLog = HttpLoggingInterceptor { mensaje ->
        Log.d("HTTP", "[${Thread.currentThread().name}] $mensaje")
    }.apply {
        level = HttpLoggingInterceptor.Level.BODY
        redactHeader("Authorization")
    }

    // Cliente OkHttp compartido (lo reutiliza el WebSocket).
    // Orden de los interceptores: primero se añade el token, luego se registra la petición.
    val clienteHttp: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor())          // 1. Añade "Authorization: Bearer <token>".
        .addInterceptor(interceptorLog)             // 2. Registra la petición (con el token oculto).
        .connectTimeout(5, TimeUnit.SECONDS)        // Si en 5 s no conecta (servidor apagado) -> error.
        .readTimeout(10, TimeUnit.SECONDS)          // Si en 10 s no responde -> error.
        .build()

    // La API de Retrofit. "by lazy" = se construye la primera vez que se usa.
    val api: BilleteraApi by lazy {
        Retrofit.Builder()
            .baseUrl(ConfigServidor.URL_BASE)                   // http://10.0.2.2:8090/
            .client(clienteHttp)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BilleteraApi::class.java)
    }
}
