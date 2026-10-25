// =====================================================================
//   Son "traductores" de datos a texto bonito: convierten montos y fechas
//   en algo que el usuario entiende ("$ 1,250.75", "Hoy", "17/10/2026 10:30").
// =====================================================================

package com.example.carriel_cuenca_billeteradistribuida.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Monto como dinero: 1250.75 -> "$ 1,250.75".
fun formatearMonto(monto: Double): String =
    "$ " + String.format(Locale.US, "%,.2f", monto)

// Molde de fecha con hora: "17/10/2026 10:30".
private val formatoFecha = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

// Fecha y hora de un movimiento, con la hora del teléfono.
fun formatearFecha(milisegundos: Long): String =
    Instant.ofEpochMilli(milisegundos)
        .atZone(ZoneId.systemDefault())
        .format(formatoFecha)

// Solo el día (sin hora): sirve para agrupar el historial por días.
fun diaDe(milisegundos: Long): LocalDate =
    Instant.ofEpochMilli(milisegundos).atZone(ZoneId.systemDefault()).toLocalDate()

// Molde de fecha sin hora: "17/10/2026".
private val formatoDia = DateTimeFormatter.ofPattern("dd/MM/yyyy")

// Texto del filtro del historial: "Hoy", "Ayer" o la fecha.
fun etiquetaDia(dia: LocalDate): String = when (dia) {
    LocalDate.now() -> "Hoy"
    LocalDate.now().minusDays(1) -> "Ayer"
    else -> dia.format(formatoDia)
}
