// =====================================================================
//   Protege las CONTRASEÑAS (las guarda "revueltas", nunca la real)
//   y crea los TOKENS, que son el "pase" de sesión del usuario.
// =====================================================================

package com.billetera.servidor

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

// Hash = "huella" de la contraseña: de la huella no se puede volver a la contraseña.
object Seguridad {

    // Se repite el cálculo 120 000 veces para que adivinar contraseñas sea muy lento.
    private const val ITERACIONES = 120_000
    private const val LONGITUD_BITS = 256

    // Números al azar que no se pueden predecir.
    private val aleatorio = SecureRandom()

    // SAL: valor al azar de cada usuario, para que dos contraseñas iguales den huellas distintas.
    fun nuevaSal(): String =
        Base64.getEncoder().encodeToString(ByteArray(16).also { aleatorio.nextBytes(it) })

    // Convierte la contraseña (con su sal) en su huella.
    fun hashear(password: String, sal: String): String {
        val especificacion = PBEKeySpec(password.toCharArray(), Base64.getDecoder().decode(sal), ITERACIONES, LONGITUD_BITS)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(especificacion).encoded
        return Base64.getEncoder().encodeToString(bytes)
    }

    // ¿La contraseña escrita es la correcta? Compara su huella con la guardada.
    fun verificar(password: String, sal: String, hashGuardado: String): Boolean =
        MessageDigest.isEqual(hashear(password, sal).toByteArray(), hashGuardado.toByteArray())

    // Crea un token: un código al azar de 64 caracteres, imposible de adivinar.
    fun nuevoToken(): String =
        ByteArray(32).also { aleatorio.nextBytes(it) }.joinToString("") { "%02x".format(it) }
}
