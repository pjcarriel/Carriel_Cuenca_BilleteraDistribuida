// =====================================================================
//   Aquí se guarda el "token": el pase que el servidor nos da al iniciar
//   sesión y que demuestra quiénes somos en cada pedido.
// =====================================================================

package com.example.carriel_cuenca_billeteradistribuida.data.remote

// Guarda el token en memoria para usarlo rápido (la copia permanente está en la base de datos).
object ProveedorToken {
    // El token actual; vacío (null) si no hay sesión.
    @Volatile
    var token: String? = null
}
