// =====================================================================
//   Revisa si una cédula ecuatoriana está bien escrita, con la misma
//   regla del Registro Civil.
// =====================================================================

package com.example.carriel_cuenca_billeteradistribuida.util

// true si la cédula es válida: 10 números, provincia correcta, tercer número menor que 6
// y el último número (de control) cuadra con la regla "módulo 10".
fun cedulaValida(cedula: String): Boolean {
    if (!Regex("^\\d{10}$").matches(cedula)) return false
    val digitos = cedula.map { it.digitToInt() }                     // "1712..." -> [1, 7, 1, 2, ...]
    val provincia = digitos[0] * 10 + digitos[1]
    if (provincia !in 1..24 && provincia != 30) return false
    if (digitos[2] >= 6) return false
    // Multiplica los 9 primeros por 2,1,2,1... (si pasa de 9, le resta 9) y los suma.
    val suma = (0 until 9).sumOf { i ->
        val producto = digitos[i] * (if (i % 2 == 0) 2 else 1)
        if (producto > 9) producto - 9 else producto
    }
    return (10 - suma % 10) % 10 == digitos[9]
}
