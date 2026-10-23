package com.example.carriel_cuenca_billeteradistribuida

import com.example.carriel_cuenca_billeteradistribuida.data.remote.ConfigServidor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Pruebas de cómo se interpreta la dirección del servidor escrita en la pantalla de login.
class ConfigServidorTest {

    @Test
    fun ipSolaQuedaEnFormaCorta() {
        assertEquals("192.168.1.5", ConfigServidor.normalizar("192.168.1.5"))
        assertEquals("10.0.2.2", ConfigServidor.normalizar("  "))              // Vacío -> emulador.
    }

    @Test
    fun httpConElPuertoDeSiempreQuedaEnFormaCorta() {
        assertEquals("192.168.1.5", ConfigServidor.normalizar("http://192.168.1.5:8090/"))
        assertEquals("192.168.1.5", ConfigServidor.normalizar("http://192.168.1.5"))
    }

    @Test
    fun httpConOtroPuertoSeRespeta() {
        assertEquals("http://192.168.1.5:9000", ConfigServidor.normalizar("192.168.1.5:9000"))
    }

    @Test
    fun urlDelTunelQuedaCompleta() {
        assertEquals("https://algo.trycloudflare.com", ConfigServidor.normalizar("https://algo.trycloudflare.com/"))
        assertEquals("https://algo.trycloudflare.com", ConfigServidor.normalizar("wss://algo.trycloudflare.com/ws"))
    }

    @Test
    fun direccionInvalidaDevuelveNull() {
        assertNull(ConfigServidor.normalizar("mi servidor"))
        assertNull(ConfigServidor.normalizar("ftp://algo.com"))
    }
}
