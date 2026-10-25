// =====================================================================
//   Dice si el teléfono está conectado en vivo con el servidor.
//   Se ve como un circulito de color en la tarjeta del saldo.
// =====================================================================

package com.example.carriel_cuenca_billeteradistribuida.model

// Los 3 estados posibles de la conexión con el servidor.
enum class EstadoConexion {
    CONECTANDO,     // Círculo ámbar: intentando conectarse.
    EN_LINEA,       // Círculo verde: conectado, las transferencias llegan al instante.
    DESCONECTADO    // Círculo rojo: sin conexión; la app vuelve a intentar sola.
}
