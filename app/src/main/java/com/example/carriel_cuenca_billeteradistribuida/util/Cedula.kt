// Paquete de utilidades generales.
package com.example.carriel_cuenca_billeteradistribuida.util

// VALIDACIÓN DE LA CÉDULA ECUATORIANA (algoritmo "módulo 10" del Registro Civil).
// Es el MISMO algoritmo que usa el servidor (Cedula.kt): validar aquí evita un viaje de red
// con una cédula mal escrita, y el servidor vuelve a validar porque nunca confía en el cliente.
//
// Reglas:
//  1. Exactamente 10 dígitos.
//  2. Los 2 primeros = provincia: 01 a 24, o 30 (ecuatorianos registrados en el exterior).
//  3. El tercer dígito es menor que 6 (personas naturales).
//  4. El ÚLTIMO dígito es el VERIFICADOR: los 9 primeros se multiplican, en orden, por 2,1,2,1,2,1,2,1,2;
//     si un producto pasa de 9 se le resta 9; se suman todos y el verificador es (10 - suma % 10) % 10.
// Ejemplo: 1712345675 -> productos 2,7,2,2,6,4,1,6,5 -> suma 35 -> (10 - 5) % 10 = 5 = último dígito. ✔
fun cedulaValida(cedula: String): Boolean {
    if (!Regex("^\\d{10}$").matches(cedula)) return false
    val digitos = cedula.map { it.digitToInt() }                     // "1712..." -> [1, 7, 1, 2, ...]
    val provincia = digitos[0] * 10 + digitos[1]
    if (provincia !in 1..24 && provincia != 30) return false
    if (digitos[2] >= 6) return false
    // sumOf recorre los 9 primeros dígitos con su posición (i): coeficiente 2 en posiciones pares, 1 en impares.
    val suma = (0 until 9).sumOf { i ->
        val producto = digitos[i] * (if (i % 2 == 0) 2 else 1)
        if (producto > 9) producto - 9 else producto
    }
    return (10 - suma % 10) % 10 == digitos[9]
}
