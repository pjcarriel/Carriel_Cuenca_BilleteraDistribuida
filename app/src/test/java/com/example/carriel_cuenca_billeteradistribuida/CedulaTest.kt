// =====================================================================
//   Son PRUEBAS automáticas del validador de cédula de la app: revisan
//   que acepte las cédulas correctas y rechace las incorrectas.
// =====================================================================

package com.example.carriel_cuenca_billeteradistribuida

import com.example.carriel_cuenca_billeteradistribuida.util.cedulaValida
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Pruebas de la cédula. assertTrue = "debe ser válida"; assertFalse = "debe ser rechazada".
class CedulaTest {

    @Test
    // Las 3 cédulas de prueba deben ser aceptadas.
    fun cedulasDeDemostracionSonValidas() {
        assertTrue(cedulaValida("1712345675"))   // Pamela (17 = Pichincha).
        assertTrue(cedulaValida("0901234567"))   // Usuario A (09 = Guayas).
        assertTrue(cedulaValida("0102030400"))   // Usuario B (01 = Azuay).
    }

    @Test
    // Si se cambia el último dígito, la cédula ya no es válida.
    fun digitoVerificadorIncorrecto() {
        assertFalse(cedulaValida("1712345670"))
        assertFalse(cedulaValida("1712345676"))
    }

    @Test
    // Cédulas mal escritas deben ser rechazadas.
    fun formatoIncorrecto() {
        assertFalse(cedulaValida("171234567"))   // 9 dígitos.
        assertFalse(cedulaValida("17123456AB"))  // Letras.
        assertFalse(cedulaValida("2512345678"))  // Provincia 25 no existe.
        assertFalse(cedulaValida("1762345678"))  // Tercer dígito 6.
    }
}
