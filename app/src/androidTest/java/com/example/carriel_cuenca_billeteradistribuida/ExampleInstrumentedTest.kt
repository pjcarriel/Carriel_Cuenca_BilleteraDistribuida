package com.example.carriel_cuenca_billeteradistribuida

import androidx.test.platform.app.InstrumentationRegistry       // Da acceso a la app que se está probando.
import androidx.test.ext.junit.runners.AndroidJUnit4            // Ejecutor de pruebas JUnit para Android.

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

// PRUEBA INSTRUMENTADA (carpeta "androidTest"): a diferencia de la unitaria (carpeta "test"),
// se ejecuta DENTRO de un teléfono o emulador, así puede usar cosas reales de Android (Context).
// Es más lenta, pero prueba la app instalada. (Viene con la plantilla; abajo, el comentario original.)
/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
// @RunWith: estas pruebas se ejecutan con el ejecutor de Android (AndroidJUnit4).
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context de la app que se está probando (información del paquete instalado).
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        // Comprueba que el nombre del paquete sea el de nuestra app.
        assertEquals("com.example.carriel_cuenca_billeteradistribuida", appContext.packageName)
    }
}