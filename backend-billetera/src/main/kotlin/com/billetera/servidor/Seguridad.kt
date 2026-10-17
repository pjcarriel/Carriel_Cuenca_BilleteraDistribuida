// Paquete del servidor.
package com.billetera.servidor

import java.security.MessageDigest            // Comparación segura de bytes.
import java.security.SecureRandom             // Números aleatorios criptográficamente seguros.
import java.util.Base64                       // Convierte bytes <-> texto.
import javax.crypto.SecretKeyFactory          // Algoritmos de derivación de claves (viene con Java).
import javax.crypto.spec.PBEKeySpec           // Parámetros de PBKDF2.

// SEGURIDAD: cifrado de contraseñas y generación de tokens.
// NUNCA guardamos la contraseña real ("demo123"): guardamos su HASH.
// Un hash es una "huella" de una sola vía: de la contraseña se obtiene el hash,
// pero del hash NO se puede volver a la contraseña.
object Seguridad {

    // PBKDF2 repite el cálculo 120 000 veces: tarda ~0,1 s por intento.
    // Para un usuario es imperceptible, pero hace MUY lento adivinar contraseñas a la fuerza.
    private const val ITERACIONES = 120_000
    private const val LONGITUD_BITS = 256

    // Generador aleatorio seguro (no predecible, a diferencia de Random()).
    private val aleatorio = SecureRandom()

    // SAL: 16 bytes aleatorios distintos para CADA usuario. Así, dos personas con la
    // misma contraseña tienen hashes diferentes, y no sirven las "tablas" de hashes precalculados.
    fun nuevaSal(): String =
        Base64.getEncoder().encodeToString(ByteArray(16).also { aleatorio.nextBytes(it) })

    // Calcula el hash de una contraseña con su sal (PBKDF2 + HMAC-SHA256).
    fun hashear(password: String, sal: String): String {
        val especificacion = PBEKeySpec(password.toCharArray(), Base64.getDecoder().decode(sal), ITERACIONES, LONGITUD_BITS)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(especificacion).encoded
        return Base64.getEncoder().encodeToString(bytes)
    }

    // ¿La contraseña escrita corresponde al hash guardado?
    // MessageDigest.isEqual compara en tiempo constante (no da pistas por lo que tarda la comparación).
    fun verificar(password: String, sal: String, hashGuardado: String): Boolean =
        MessageDigest.isEqual(hashear(password, sal).toByteArray(), hashGuardado.toByteArray())

    // TOKEN de sesión: 32 bytes aleatorios en hexadecimal (64 caracteres). Imposible de adivinar.
    fun nuevoToken(): String =
        ByteArray(32).also { aleatorio.nextBytes(it) }.joinToString("") { "%02x".format(it) }
}
