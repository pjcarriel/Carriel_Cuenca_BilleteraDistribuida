// Paquete del servidor.
package com.billetera.servidor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.sql.Connection
import java.sql.SQLIntegrityConstraintViolationException     // Error de MySQL al violar un UNIQUE.
import java.sql.Statement

private val log = LoggerFactory.getLogger("UsuarioRepositorio")

// Minutos SIN ACTIVIDAD tras los cuales la sesión se cierra sola.
// 15 (antes 5): deja margen para explicar en la defensa sin que la sesión de la app o de Postman expire.
const val MINUTOS_INACTIVIDAD = 15

// Saldo de bienvenida para cada cuenta nueva.
private val BONO_BIENVENIDA = BigDecimal("100.00")

// Reglas de los datos de registro (EXPRESIONES REGULARES).
// "^" y "$" = el texto COMPLETO debe cumplir la regla. "(?=.*[A-Za-z])" y "(?=.*\\d)" son "lookaheads":
// exigen que exista al menos una letra y al menos un dígito en alguna parte. ".{6,50}" = de 6 a 50 caracteres.
private val PATRON_USUARIO = Regex("^[a-z0-9_]{4,20}$")          // 4 a 20: letras minúsculas, números o "_".
private val PATRON_PASSWORD = Regex("^(?=.*[A-Za-z])(?=.*\\d).{6,50}$")  // Mínimo 6, con al menos una letra y un número.

// REGISTRO, LOGIN, LOGOUT y VALIDACIÓN DE SESIONES.
// Igual que en la app: el trabajo con MySQL (que bloquea el hilo) se hace en Dispatchers.IO.
object UsuarioRepositorio {

    // Crea el usuario, su cuenta (número automático), le abona el bono y abre una sesión.
    suspend fun registrar(req: RegistroRequest): SesionRespuesta = withContext(Dispatchers.IO) {
        val usuario = req.usuario.trim().lowercase()
        val nombre = req.nombre.trim()
        if (!PATRON_USUARIO.matches(usuario)) throw datoInvalido("El usuario debe tener de 4 a 20 letras, números o _")
        if (nombre.length !in 3..60) throw datoInvalido("Escribe tu nombre completo (3 a 60 caracteres)")
        if (!PATRON_PASSWORD.matches(req.password)) throw datoInvalido("La contraseña debe tener al menos 6 caracteres, con letras y números")

        // El hash se calcula FUERA de la transacción (tarda ~0,1 s y no necesita la base de datos).
        val sal = Seguridad.nuevaSal()
        val hash = Seguridad.hashear(req.password, sal)
        // Los 4 pasos van en UNA transacción: si alguno falla, no queda un usuario sin cuenta ni una cuenta sin bono.
        try {
            BaseDeDatos.transaccion { con ->
                // 1. Usuario.
                val id = con.prepareStatement(
                    "INSERT INTO usuarios (usuario, nombre, password_hash, sal) VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
                ).use { ps ->
                    ps.setString(1, usuario); ps.setString(2, nombre); ps.setString(3, hash); ps.setString(4, sal)
                    ps.executeUpdate()
                    ps.generatedKeys.use { it.next(); it.getLong(1) }
                }
                // 2. Cuenta con el siguiente número libre.
                val numero = siguienteNumeroCuenta(con)
                con.prepareStatement("INSERT INTO cuentas (numero, titular, saldo, usuario_id) VALUES (?, ?, ?, ?)").use { ps ->
                    ps.setString(1, numero); ps.setString(2, nombre); ps.setBigDecimal(3, BONO_BIENVENIDA); ps.setLong(4, id)
                    ps.executeUpdate()
                }
                // 3. Comprobante del bono (aparecerá en el historial de la app).
                registrarTransaccion(con, "DEPOSITO", numero, null, BONO_BIENVENIDA, "Bono de bienvenida")
                // 4. Sesión.
                val token = crearSesion(con, id)
                log.info("Registro: usuario '$usuario' -> cuenta $numero (bono de $BONO_BIENVENIDA)")
                SesionRespuesta(token, usuario, nombre, numero, BONO_BIENVENIDA.toDouble(), MINUTOS_INACTIVIDAD)
            }
        } catch (e: SQLIntegrityConstraintViolationException) {
            throw usuarioExiste(usuario)       // El UNIQUE de "usuario" detectó un duplicado (aunque dos lleguen a la vez).
        }
    }

    // Verifica usuario y contraseña y abre una sesión nueva.
    suspend fun login(req: LoginRequest): SesionRespuesta = withContext(Dispatchers.IO) {
        val usuario = req.usuario.trim().lowercase()
        BaseDeDatos.transaccion { con ->
            // CONSULTA: JOIN une cada usuario con SU cuenta (c.usuario_id = u.id) para obtener en una sola
            // lectura el hash, la sal, el número de cuenta y el saldo. "u" y "c" son alias (nombres cortos).
            con.prepareStatement(
                """
                SELECT u.id, u.nombre, u.password_hash, u.sal, c.numero, c.saldo
                FROM usuarios u JOIN cuentas c ON c.usuario_id = u.id
                WHERE u.usuario = ?
                """.trimIndent()
            ).use { ps ->
                ps.setString(1, usuario)
                ps.executeQuery().use { rs ->
                    // Si no existe o la contraseña no coincide: el MISMO error (no damos pistas).
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

    // Cierra la sesión (borra el token: deja de servir inmediatamente).
    suspend fun logout(token: String) = withContext(Dispatchers.IO) {
        BaseDeDatos.transaccion { con ->
            con.prepareStatement("DELETE FROM sesiones WHERE token = ?").use { it.setString(1, token); it.executeUpdate() }
        }
        log.info("Logout: sesión cerrada")
    }

    // Valida un token en CADA petición protegida:
    //  - si no existe -> 401 NO_AUTENTICADO
    //  - si lleva más de MINUTOS_INACTIVIDAD sin uso -> se borra y 401 SESION_EXPIRADA
    //  - si es válido -> se renueva su última actividad (el contador vuelve a cero)
    suspend fun validarSesion(token: String): SesionUsuario = withContext(Dispatchers.IO) {
        // "SesionUsuario?" = null significa "expiró". Lanzamos el error DESPUÉS del commit,
        // para que el DELETE de la sesión vencida sí se guarde (un error dentro haría rollback).
        val sesion: SesionUsuario? = BaseDeDatos.transaccion { con ->
            // CONSULTA: une sesión -> usuario -> cuenta, y MySQL calcula cuántos segundos pasaron
            // desde la última actividad (TIMESTAMPDIFF) usando SU propio reloj (NOW(3)), no el del celular.
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
                        con.prepareStatement("DELETE FROM sesiones WHERE token = ?").use { it.setString(1, token); it.executeUpdate() }
                        log.info("Sesión de '${rs.getString("usuario")}' EXPIRADA por inactividad")
                        null
                    } else {
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

    // ------------------------- ayudantes privados -------------------------

    // Inserta una sesión nueva y devuelve su token.
    private fun crearSesion(con: Connection, usuarioId: Long): String {
        val token = Seguridad.nuevoToken()
        con.prepareStatement("INSERT INTO sesiones (token, usuario_id) VALUES (?, ?)").use { ps ->
            ps.setString(1, token); ps.setLong(2, usuarioId); ps.executeUpdate()
        }
        return token
    }

    // Siguiente número de cuenta (máximo actual + 1). "FOR UPDATE" bloquea las filas leídas:
    // si dos personas se registran a la vez, la segunda espera y obtiene el número siguiente.
    private fun siguienteNumeroCuenta(con: Connection): String {
        val numeros = con.prepareStatement("SELECT numero FROM cuentas FOR UPDATE").use { ps ->
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.getString(1)) } }
        }
        // mapNotNull + toLongOrNull: ignora números que no sean dígitos; "?: 1000" si la tabla está vacía.
        return ((numeros.mapNotNull { it.toLongOrNull() }.maxOrNull() ?: 1000L) + 1).toString()
    }
}
