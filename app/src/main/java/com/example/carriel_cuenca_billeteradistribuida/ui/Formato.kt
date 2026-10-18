// Paquete de la interfaz gráfica.
package com.example.carriel_cuenca_billeteradistribuida.ui

import java.time.Instant                       // Un instante en el tiempo.
import java.time.LocalDate                      // Una fecha (sin hora).
import java.time.ZoneId                        // Zona horaria.
import java.time.format.DateTimeFormatter      // Formato de fecha.
import java.util.Locale                        // Configuración regional (separadores de miles).

// FUNCIONES DE FORMATO usadas por varias pantallas (no dibujan nada, solo convierten a texto).

// Convierte 1250.75 en "$ 1,250.75" (2 decimales y separador de miles).
fun formatearMonto(monto: Double): String =
    "$ " + String.format(Locale.US, "%,.2f", monto)

// Formato de fecha: día/mes/año hora:minuto. Ej: "17/10/2026 10:30".
private val formatoFecha = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

// Convierte milisegundos (Long) en un texto de fecha legible, en la zona horaria del teléfono.
fun formatearFecha(milisegundos: Long): String =
    Instant.ofEpochMilli(milisegundos)          // Milisegundos -> instante en el tiempo.
        .atZone(ZoneId.systemDefault())         // Instante -> fecha/hora local del teléfono.
        .format(formatoFecha)                   // Fecha/hora -> texto.

// El día (fecha sin hora) de unos milisegundos, en la zona del teléfono. Sirve para agrupar el historial.
fun diaDe(milisegundos: Long): LocalDate =
    Instant.ofEpochMilli(milisegundos).atZone(ZoneId.systemDefault()).toLocalDate()

private val formatoDia = DateTimeFormatter.ofPattern("dd/MM/yyyy")

// Etiqueta amigable de un día: "Hoy", "Ayer" o la fecha. Se muestra en el filtro del historial.
fun etiquetaDia(dia: LocalDate): String = when (dia) {
    LocalDate.now() -> "Hoy"
    LocalDate.now().minusDays(1) -> "Ayer"
    else -> dia.format(formatoDia)
}
