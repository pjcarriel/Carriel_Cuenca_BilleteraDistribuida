// =====================================================================
//   Revisa si una CÉDULA ecuatoriana es válida. El servidor la vuelve a
//   revisar aunque la app ya lo hizo, porque no se confía en el celular.
// =====================================================================

package com.billetera.servidor

// Devuelve true si la cédula cumple las reglas: 10 dígitos, provincia 01-24 o 30,
// tercer dígito menor que 6 y último dígito de control correcto.
fun cedulaValida(cedula: String): Boolean {
    if (!Regex("^\\d{10}$").matches(cedula)) return false
    val digitos = cedula.map { it.digitToInt() }
    val provincia = digitos[0] * 10 + digitos[1]
    if (provincia !in 1..24 && provincia != 30) return false
    if (digitos[2] >= 6) return false
    // Los 9 primeros se multiplican por 2,1,2,1...; si pasa de 9 se le resta 9; y se suman.
    val suma = (0 until 9).sumOf { i ->
        val producto = digitos[i] * (if (i % 2 == 0) 2 else 1)
        if (producto > 9) producto - 9 else producto
    }
    // El último dígito debe salir de esa suma.
    return (10 - suma % 10) % 10 == digitos[9]
}
