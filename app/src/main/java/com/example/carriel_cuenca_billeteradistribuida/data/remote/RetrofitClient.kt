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

    // Interceptor de LOG: muestra en el Logcat (tag "HTTP") cada petición y respuesta,
    // incluyendo el NOMBRE DEL HILO en el que OkHttp hace la llamada de red.
    // Level.BODY = muestra método, URL, código HTTP y el JSON completo.
    private val interceptorLog = HttpLoggingInterceptor { mensaje ->
        Log.d("HTTP", "[${Thread.currentThread().name}] $mensaje")
    }.apply { level = HttpLoggingInterceptor.Level.BODY }

    // Cliente OkHttp compartido. "val" público porque la Fase 5 lo reutilizará para el WebSocket.
    val clienteHttp: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(interceptorLog)             // Agregamos el log.
        .connectTimeout(5, TimeUnit.SECONDS)        // Si en 5 s no conecta (servidor apagado) -> error.
        .readTimeout(10, TimeUnit.SECONDS)          // Si en 10 s no responde -> error.
        .build()

    // La API de Retrofit. "by lazy" = se construye la PRIMERA vez que alguien la usa (no antes).
    val api: BilleteraApi by lazy {
        Retrofit.Builder()
            .baseUrl(ConfigServidor.URL_BASE)                   // http://10.0.2.2:8090/
            .client(clienteHttp)                                // Usamos nuestro OkHttp con logs.
            .addConverterFactory(GsonConverterFactory.create()) // JSON con Gson.
            .build()
            .create(BilleteraApi::class.java)                   // Retrofit implementa la interfaz.
    }
}
