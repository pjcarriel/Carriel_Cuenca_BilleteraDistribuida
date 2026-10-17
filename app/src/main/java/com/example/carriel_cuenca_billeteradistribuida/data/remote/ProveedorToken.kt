// Paquete de comunicación con el servidor.
package com.example.carriel_cuenca_billeteradistribuida.data.remote

// Guarda en MEMORIA el token de la sesión activa, para que el interceptor lo añada a cada petición.
// (La copia permanente vive en Room, tabla "sesion"; esta es la de acceso rápido).
object ProveedorToken {
    // @Volatile: varios hilos lo leen (OkHttp) y lo escriben (login/logout); así todos ven el valor actual.
    // "String?" = null cuando no hay sesión.
    @Volatile
    var token: String? = null
}
