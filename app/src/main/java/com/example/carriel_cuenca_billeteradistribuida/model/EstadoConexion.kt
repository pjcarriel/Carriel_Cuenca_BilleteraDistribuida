// Paquete de los modelos de datos.
package com.example.carriel_cuenca_billeteradistribuida.model

// Estado del túnel WebSocket con el servidor (lo muestra el indicador de la tarjeta de saldo).
// "enum class": solo existen estos tres estados posibles.
enum class EstadoConexion {
    CONECTANDO,     // Intentando abrir el WebSocket (círculo ámbar "Conectando...").
    EN_LINEA,       // Túnel abierto: las transferencias llegan en tiempo real (círculo verde).
    DESCONECTADO    // Sin túnel: el servidor no responde; se reintentará solo (círculo rojo).
}
