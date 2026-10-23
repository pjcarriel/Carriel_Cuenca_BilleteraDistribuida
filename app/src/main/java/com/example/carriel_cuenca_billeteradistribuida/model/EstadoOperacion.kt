// Paquete de los modelos de datos.
package com.example.carriel_cuenca_billeteradistribuida.model

// "sealed class" (clase SELLADA) = una familia CERRADA de clases.
// Es como un enum, pero cada opción puede llevar sus PROPIOS datos:
// Exito lleva un mensaje, Error lleva otro mensaje, Inactivo no lleva nada.
// Como la familia está cerrada, un "when" sobre EstadoOperacion
// obliga a contemplar TODOS los casos (el compilador lo verifica).
sealed class EstadoOperacion {

    // "data object" = una opción SIN datos (solo existe una instancia).
    // No hay ninguna operación en curso ni mensaje que mostrar.
    data object Inactivo : EstadoOperacion()

    // Hay una operación en curso (esperando a Room o, más adelante, al servidor).
    // (Hoy espera la respuesta del servidor; mientras tanto se ignoran nuevos envíos.)
    data object Procesando : EstadoOperacion()

    // La operación terminó bien. "data class" = opción CON datos.
    data class Exito(val mensaje: String) : EstadoOperacion()

    // La operación falló (ej. en la Fase 4: "Saldo insuficiente").
    data class Error(val mensaje: String) : EstadoOperacion()
}
