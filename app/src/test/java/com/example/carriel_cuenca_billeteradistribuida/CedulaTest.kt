package com.example.carriel_cuenca_billeteradistribuida

import com.example.carriel_cuenca_billeteradistribuida.util.cedulaValida
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Pruebas del validador de cédula ecuatoriana (util/Cedula.kt).
// Las cédulas usadas son FICTICIAS (las de los datos de demostración del servidor).
class CedulaTest {

    @Test
    // Las tres cédulas de demostración cumplen el algoritmo módulo 10.
    fun cedulasDeDemostracionSonValidas() {
        assertTrue(cedulaValida("1712345675"))   // Pamela (provincia 17 = Pichincha).
        assertTrue(cedulaValida("0901234567"))   // Usuario A (09 = Guayas).
        assertTrue(cedulaValida("0102030400"))   // Usuario B (01 = Azuay).
    }

    @Test
    // Cambiar SOLO el dígito verificador vuelve inválida la cédula.
    fun digitoVerificadorIncorrecto() {
        assertFalse(cedulaValida("1712345670"))
        assertFalse(cedulaValida("1712345676"))
    }

    @Test
    // Formato incorrecto: menos de 10 dígitos, letras, provincia 25 o tercer dígito 6 o más.
    fun formatoIncorrecto() {
        assertFalse(cedulaValida("171234567"))   // 9 dígitos.
        assertFalse(cedulaValida("17123456AB"))  // Letras.
        assertFalse(cedulaValida("2512345678"))  // Provincia 25 no existe.
        assertFalse(cedulaValida("1762345678"))  // Tercer dígito 6.
    }
}
