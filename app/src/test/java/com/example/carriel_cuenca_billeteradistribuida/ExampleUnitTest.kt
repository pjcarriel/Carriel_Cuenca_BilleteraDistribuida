package com.example.carriel_cuenca_billeteradistribuida

import org.junit.Test                                           // Anotación @Test de JUnit 4.

import org.junit.Assert.*                                       // "*" importa todas las aserciones (assertEquals, assertTrue...).

// PRUEBA UNITARIA DE EJEMPLO (viene con la plantilla de Android Studio).
// Se ejecuta en la PC (JVM), NO en el teléfono. El bloque de abajo es el comentario original en inglés.
/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    // @Test marca la función como una prueba que JUnit debe ejecutar.
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)                                  // Comprueba que 2 + 2 sea 4; si no, la prueba falla.
    }
}