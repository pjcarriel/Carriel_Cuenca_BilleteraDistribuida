// =====================================================================
//   Maneja a los USUARIOS: registrarse, iniciar y cerrar sesión, y revisar
//   que la sesión siga activa (se cierra sola tras 15 minutos sin uso).
// =====================================================================

package com.billetera.servidor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.sql.Connection
import java.sql.SQLIntegrityConstraintViolationException
import java.sql.Statement

private val log = LoggerFactory.getLogger("UsuarioRepositorio")

// Minutos sin uso tras los cuales se cierra la sesión.
const val MINUTOS_INACTIVIDAD = 15

// Saldo de regalo para cada cuenta nueva.
private val BONO_BIENVENIDA = BigDecimal("100.00")

// Usuario: 4 a 20 minúsculas, números o "_". Contraseña: 6 a 50 caracteres con letras y números.
private val PATRON_USUARIO = Regex("^[a-z0-9_]{4,20}$")
private val PATRON_PASSWORD = Regex("^(?=.*[A-Za-z])(?=.*\\d).{6,50}$")

// Registro, login, logout y revisión de sesiones.
object UsuarioRepositorio {

    // REGISTRO: crea el usuario, su cuenta con $100 de regalo y abre su sesión.
    suspend fun registrar(req: RegistroRequest): SesionRespuesta = withContext(Dispatchers.IO) {
        val usuario = req.usuario.trim().lowercase()
        val nombre = req.nombre.trim()
        if (!PATRON_USUARIO.matches(usuario)) throw datoInvalido("El usuario debe tener de 4 a 20 letras, números o _")
        if (nombre.length !in 3..60) throw datoInvalido("Escribe tu nombre completo (3 a 60 caracteres)")
        if (!PATRON_PASSWORD.matches(req.password)) throw datoInvalido("La contraseña debe tener al menos 6 caracteres, con letras y números")

        val sal = Seguridad.nuevaSal()
        val hash = Seguridad.hashear(req.password, sal)
        // Los 4 pasos se guardan juntos o ninguno (nunca queda un usuario sin cuenta).
        try {
            BaseDeDatos.transaccion { con ->
                // 1. Guardar el usuario.
                val id = con.prepareStatement(
                    "INSERT INTO usuarios (usuario, nombre, password_hash, sal) VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
                ).use { ps ->
                    ps.setString(1, usuario); ps.setString(2, nombre); ps.setString(3, hash); ps.setString(4, sal)
                    ps.executeUpdate()
                    ps.generatedKeys.use { it.next(); it.getLong(1) }
                }
                // 2. Crear su cuenta.
                val numero = siguienteNumeroCuenta(con)
                con.prepareStatement("INSERT INTO cuentas (numero, titular, saldo, usuario_id) VALUES (?, ?, ?, ?)").use { ps ->
                    ps.setString(1, numero); ps.setString(2, nombre); ps.setBigDecimal(3, BONO_BIENVENIDA); ps.setLong(4, id)
                    ps.executeUpdate()
                }
                // 3. Comprobante del bono.
                registrarTransaccion(con, "DEPOSITO", numero, null, BONO_BIENVENIDA, "Bono de bienvenida")
                // 4. Abrir la sesión.
                val token = crearSesion(con, id)
                log.info("Registro: usuario '$usuario' -> cuenta $numero (bono de $BONO_BIENVENIDA)")
                SesionRespuesta(token, usuario, nombre, numero, BONO_BIENVENIDA.toDouble(), MINUTOS_INACTIVIDAD)
            }
        } catch (e: SQLIntegrityConstraintViolationException) {
            throw usuarioExiste(usuario)       // Ese nombre de usuario ya existe.
        }
    }

    // LOGIN: revisa usuario y contraseña y abre una sesión nueva.
    suspend fun login(req: LoginRequest): SesionRespuesta = withContext(Dispatchers.IO) {
        val usuario = req.usuario.trim().lowercase()
        BaseDeDatos.transaccion { con ->
            // Busca el usuario junto con su cuenta.
            con.prepareStatement(
                """
                SELECT u.id, u.nombre, u.password_hash, u.sal, c.numero, c.saldo
                FROM usuarios u JOIN cuentas c ON c.usuario_id = u.id
                WHERE u.usuario = ?
                """.trimIndent()
            ).use { ps ->
                ps.setString(1, usuario)
                ps.executeQuery().use { rs ->
                    // Usuario o contraseña mal: mismo error, para no dar pistas.
                    if (!rs.next() || !Seguridad.verificar(req.password, rs.getString("sal"), rs.getString("password_hash"))) {
                        log.warn("Login fallido para '$usuario'")
                        throw credencialesInvalidas()
                    }
                    val token = crearSesion(con, rs.getLong("id"))
                    log.info("Login: '$usuario' (cuenta ${rs.getString("numero")})")
                    SesionRespuesta(token, usuario, rs.getString("nombre"), rs.getString("numero"),
                        rs.getBigDecimal("saldo").toDouble(), MINUTOS_INACTIVIDAD)
                }
            }
        }
    }

    // LOGOUT: borra el token, así deja de servir.
    suspend fun logout(token: String) = withContext(Dispatchers.IO) {
        BaseDeDatos.transaccion { con ->
            con.prepareStatement("DELETE FROM sesiones WHERE token = ?").use { it.setString(1, token); it.executeUpdate() }
        }
        log.info("Logout: sesión cerrada")
    }

    // Revisa el token en cada petición: si no existe o venció -> error 401;
    // si está bien, reinicia el contador de inactividad.
    suspend fun validarSesion(token: String): SesionUsuario = withContext(Dispatchers.IO) {
        // null = la sesión venció (el error se lanza al final para que sí se borre).
        val sesion: SesionUsuario? = BaseDeDatos.transaccion { con ->
            // Busca la sesión y calcula cuántos segundos lleva sin uso.
            con.prepareStatement(
                """
                SELECT s.usuario_id, u.usuario, u.nombre, c.numero,
                       TIMESTAMPDIFF(SECOND, s.ultima_actividad, NOW(3)) AS segundos_inactivo
                FROM sesiones s
                JOIN usuarios u ON u.id = s.usuario_id
                JOIN cuentas  c ON c.usuario_id = u.id
                WHERE s.token = ?
                """.trimIndent()
            ).use { ps ->
                ps.setString(1, token)
                ps.executeQuery().use { rs ->
                    if (!rs.next()) throw noAutenticado()
                    if (rs.getLong("segundos_inactivo") > MINUTOS_INACTIVIDAD * 60L) {
                        // Venció: se borra.
                        con.prepareStatement("DELETE FROM sesiones WHERE token = ?").use { it.setString(1, token); it.executeUpdate() }
                        log.info("Sesión de '${rs.getString("usuario")}' EXPIRADA por inactividad")
                        null
                    } else {
                        // Sigue activa: se actualiza la hora del último uso.
                        con.prepareStatement("UPDATE sesiones SET ultima_actividad = NOW(3) WHERE token = ?").use {
                            it.setString(1, token); it.executeUpdate()
                        }
                        SesionUsuario(rs.getLong("usuario_id"), rs.getString("usuario"), rs.getString("nombre"), rs.getString("numero"))
                    }
                }
            }
        }
        sesion ?: throw sesionExpirada()
    }

    // Crea una sesión nueva y devuelve su token.
    private fun crearSesion(con: Connection, usuarioId: Long): String {
        val token = Seguridad.nuevoToken()
        con.prepareStatement("INSERT INTO sesiones (token, usuario_id) VALUES (?, ?)").use { ps ->
            ps.setString(1, token); ps.setLong(2, usuarioId); ps.executeUpdate()
        }
        return token
    }

    // Siguiente número de cuenta libre (el mayor + 1). Nunca se repite aunque dos se registren a la vez.
    private fun siguienteNumeroCuenta(con: Connection): String {
        val numeros = con.prepareStatement("SELECT numero FROM cuentas FOR UPDATE").use { ps ->
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.getString(1)) } }
        }
        return ((numeros.mapNotNull { it.toLongOrNull() }.maxOrNull() ?: 1000L) + 1).toString()
    }
}
