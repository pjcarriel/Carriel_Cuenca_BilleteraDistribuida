package com.example.carriel_cuenca_billeteradistribuida

// JUnit 4: assertEquals(esperado, real) falla si no son iguales; assertNull falla si el valor NO es null.
import com.example.carriel_cuenca_billeteradistribuida.data.remote.ConfigServidor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Pruebas de cómo se interpreta la dirección del servidor escrita en la pantalla de login.
// PRUEBA UNITARIA (carpeta "test"): corre en la PC (JVM), sin teléfono ni emulador; por eso es rápida.
// Cada función marcada con @Test es una prueba independiente que JUnit ejecuta por separado.
class ConfigServidorTest {

    @Test
    // Una IP sola se deja igual; un texto vacío usa la IP del EMULADOR (10.0.2.2 = la PC).
    fun ipSolaQuedaEnFormaCorta() {
        assertEquals("192.168.1.5", ConfigServidor.normalizar("192.168.1.5"))
        assertEquals("10.0.2.2", ConfigServidor.normalizar("  "))              // Vacío -> emulador.
    }

    @Test
    // Si se escribe http:// con el puerto de siempre (8090), se guarda solo la IP.
    fun httpConElPuertoDeSiempreQuedaEnFormaCorta() {
        assertEquals("192.168.1.5", ConfigServidor.normalizar("http://192.168.1.5:8090/"))
        assertEquals("192.168.1.5", ConfigServidor.normalizar("http://192.168.1.5"))
    }

    @Test
    // Si el puerto es distinto de 8090, se guarda la dirección completa para respetarlo.
    fun httpConOtroPuertoSeRespeta() {
        assertEquals("http://192.168.1.5:9000", ConfigServidor.normalizar("192.168.1.5:9000"))
    }

    @Test
    // La URL del túnel de Cloudflare (https) se guarda completa; una wss://.../ws se convierte a https.
    fun urlDelTunelQuedaCompleta() {
        assertEquals("https://algo.trycloudflare.com", ConfigServidor.normalizar("https://algo.trycloudflare.com/"))
        assertEquals("https://algo.trycloudflare.com", ConfigServidor.normalizar("wss://algo.trycloudflare.com/ws"))
    }

    @Test
    // Un texto con espacios o un protocolo no soportado (ftp) no es una dirección válida -> null.
    fun direccionInvalidaDevuelveNull() {
        assertNull(ConfigServidor.normalizar("mi servidor"))
        assertNull(ConfigServidor.normalizar("ftp://algo.com"))
    }
}
