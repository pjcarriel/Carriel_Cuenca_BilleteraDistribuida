// =====================================================================
//   Son PRUEBAS automáticas de cómo la app entiende la dirección del
//   servidor que el usuario escribe en la pantalla de login.
// =====================================================================

package com.example.carriel_cuenca_billeteradistribuida

import com.example.carriel_cuenca_billeteradistribuida.data.remote.ConfigServidor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Pruebas de la dirección del servidor. assertEquals = "debe quedar así"; assertNull = "debe rechazarse".
class ConfigServidorTest {

    @Test
    // Una IP sola queda igual; si no se escribe nada, se usa la del emulador.
    fun ipSolaQuedaEnFormaCorta() {
        assertEquals("192.168.1.5", ConfigServidor.normalizar("192.168.1.5"))
        assertEquals("10.0.2.2", ConfigServidor.normalizar("  "))
    }

    @Test
    // Con el puerto de siempre (8090) se guarda solo la IP.
    fun httpConElPuertoDeSiempreQuedaEnFormaCorta() {
        assertEquals("192.168.1.5", ConfigServidor.normalizar("http://192.168.1.5:8090/"))
        assertEquals("192.168.1.5", ConfigServidor.normalizar("http://192.168.1.5"))
    }

    @Test
    // Con otro puerto se guarda la dirección completa.
    fun httpConOtroPuertoSeRespeta() {
        assertEquals("http://192.168.1.5:9000", ConfigServidor.normalizar("192.168.1.5:9000"))
    }

    @Test
    // La dirección del túnel de Cloudflare se guarda completa, en https.
    fun urlDelTunelQuedaCompleta() {
        assertEquals("https://algo.trycloudflare.com", ConfigServidor.normalizar("https://algo.trycloudflare.com/"))
        assertEquals("https://algo.trycloudflare.com", ConfigServidor.normalizar("wss://algo.trycloudflare.com/ws"))
    }

    @Test
    // Un texto que no es dirección se rechaza.
    fun direccionInvalidaDevuelveNull() {
        assertNull(ConfigServidor.normalizar("mi servidor"))
        assertNull(ConfigServidor.normalizar("ftp://algo.com"))
    }
}
