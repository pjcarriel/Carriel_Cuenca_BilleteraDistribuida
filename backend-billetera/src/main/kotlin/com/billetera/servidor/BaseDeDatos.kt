// Paquete del servidor.
package com.billetera.servidor

import com.zaxxer.hikari.HikariConfig         // Configuración del pool de conexiones.
import com.zaxxer.hikari.HikariDataSource     // Pool de conexiones a MySQL.
import org.slf4j.LoggerFactory                // Para escribir logs.
import java.io.File                           // Para leer el archivo db.properties.
import java.sql.Connection                    // Una conexión a la base de datos.
import java.sql.Statement
import java.util.Properties                   // Lee archivos "clave=valor".

private val log = LoggerFactory.getLogger("BaseDeDatos")

// Contraseña de los usuarios de DEMOSTRACIÓN (documentada en el README). Los usuarios reales eligen la suya.
const val PASSWORD_DEMO = "demo123"

// Acceso a MySQL. "object" = una sola instancia para todo el servidor.
object BaseDeDatos {

    // "lateinit" = se inicializa más tarde (en iniciar()), pero nunca será null al usarse.
    private lateinit var pool: HikariDataSource

    // Lee db.properties, abre el pool de conexiones y prepara las tablas.
    fun iniciar() {
        val archivo = File("db.properties")
        if (!archivo.exists()) {
            error("No existe db.properties. Copia db.properties.example como db.properties y pon tus datos de MySQL.")
        }
        val props = Properties().apply { archivo.inputStream().use { load(it) } }
        val config = HikariConfig().apply {
            jdbcUrl = props.getProperty("db.url")
            username = props.getProperty("db.user")
            password = props.getProperty("db.password")
            maximumPoolSize = 10            // Hasta 10 operaciones simultáneas contra MySQL.
        }
        pool = HikariDataSource(config)
        log.info("Conectado a MySQL: ${config.jdbcUrl.substringBefore("?")}")
        crearTablas()
    }

    // Crea las tablas que falten y agrega datos de demostración (solo la primera vez).
    private fun crearTablas() {
        pool.connection.use { con ->
            con.createStatement().use { st ->
                // USUARIOS: personas registradas. Guardamos el HASH de la contraseña y su SAL, nunca la contraseña.
                st.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS usuarios (
                        id            BIGINT AUTO_INCREMENT PRIMARY KEY,
                        usuario       VARCHAR(20)  NOT NULL UNIQUE,
                        nombre        VARCHAR(100) NOT NULL,
                        password_hash VARCHAR(100) NOT NULL,
                        sal           VARCHAR(50)  NOT NULL,
                        creado        TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                    )
                    """.trimIndent()
                )
                // CUENTAS: saldo oficial. CHECK impide saldos negativos.
                st.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS cuentas (
                        numero     VARCHAR(20)   PRIMARY KEY,
                        titular    VARCHAR(100)  NOT NULL,
                        saldo      DECIMAL(12,2) NOT NULL DEFAULT 0,
                        usuario_id BIGINT        NULL UNIQUE,
                        CHECK (saldo >= 0)
                    )
                    """.trimIndent()
                )
                // Bases creadas en la Fase 3.5 no tienen usuario_id: se agrega (MIGRACIÓN manual).
                if (!existeColumna(con, "cuentas", "usuario_id")) {
                    st.executeUpdate("ALTER TABLE cuentas ADD COLUMN usuario_id BIGINT NULL UNIQUE")
                    log.info("Migración: columna cuentas.usuario_id agregada")
                }
                // TRANSACCIONES: historial oficial del servidor.
                st.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS transacciones (
                        id             BIGINT AUTO_INCREMENT PRIMARY KEY,
                        tipo           VARCHAR(30)   NOT NULL,
                        cuenta_origen  VARCHAR(20)   NOT NULL,
                        cuenta_destino VARCHAR(20)   NULL,
                        monto          DECIMAL(12,2) NOT NULL,
                        referencia     VARCHAR(150)  NULL,
                        fecha          TIMESTAMP(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                        FOREIGN KEY (cuenta_origen) REFERENCES cuentas(numero)
                    )
                    """.trimIndent()
                )
                // SESIONES: tokens activos y su ÚLTIMA ACTIVIDAD (para cerrar por inactividad).
                st.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS sesiones (
                        token            CHAR(64)     PRIMARY KEY,
                        usuario_id       BIGINT       NOT NULL,
                        creada           TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                        ultima_actividad TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                        FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
                    )
                    """.trimIndent()
                )
                // CONTRATOS: facturas de servicios básicos (simula las empresas de luz, agua e internet).
                st.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS contratos (
                        codigo     VARCHAR(20)   PRIMARY KEY,
                        servicio   VARCHAR(10)   NOT NULL,
                        titular    VARCHAR(100)  NOT NULL,
                        monto      DECIMAL(12,2) NOT NULL,
                        pagado     BOOLEAN       NOT NULL DEFAULT FALSE,
                        pagado_por VARCHAR(20)   NULL,
                        fecha_pago TIMESTAMP(3)  NULL
                    )
                    """.trimIndent()
                )
                // Cuentas de demostración (INSERT IGNORE = si ya existen, no hace nada).
                // La cuenta 9999 (laboratorio) tiene saldo alto para lanzar muchas transferencias en la demo.
                st.executeUpdate(
                    """
                    INSERT IGNORE INTO cuentas (numero, titular, saldo) VALUES
                        ('1001', 'Pamela Carriel', 1250.75),
                        ('1002', 'Usuario Postman A', 500.00),
                        ('1003', 'Usuario Postman B', 300.00),
                        ('9999', 'Laboratorio de Pruebas', 100000.00)
                    """.trimIndent()
                )
                // Facturas de ejemplo: 3 por servicio (sin pagar).
                st.executeUpdate(
                    """
                    INSERT IGNORE INTO contratos (codigo, servicio, titular, monto) VALUES
                        ('445566', 'LUZ', 'Pamela Carriel', 32.75),
                        ('445567', 'LUZ', 'Pamela Carriel', 28.10),
                        ('112233', 'LUZ', 'Usuario Postman A', 41.10),
                        ('778899', 'AGUA', 'Pamela Carriel', 18.40),
                        ('778900', 'AGUA', 'Pamela Carriel', 21.05),
                        ('334455', 'AGUA', 'Usuario Postman B', 15.60),
                        ('NET2026', 'INTERNET', 'Pamela Carriel', 25.00),
                        ('NET2027', 'INTERNET', 'Pamela Carriel', 25.00),
                        ('NET3001', 'INTERNET', 'Usuario Postman A', 30.00)
                    """.trimIndent()
                )
            }
            // Usuarios de demostración, enlazados a las cuentas 1001, 1002 y 1003.
            crearUsuarioDemo(con, "pamela", "Pamela Carriel", "1001")
            crearUsuarioDemo(con, "usuarioa", "Usuario Postman A", "1002")
            crearUsuarioDemo(con, "usuariob", "Usuario Postman B", "1003")
            crearUsuarioDemo(con, "laboratorio", "Laboratorio de Pruebas", "9999")
        }
        log.info("Tablas listas (usuarios, cuentas, transacciones, sesiones, contratos)")
    }

    // ¿Existe una columna en una tabla? (consulta el catálogo de MySQL: information_schema).
    private fun existeColumna(con: Connection, tabla: String, columna: String): Boolean =
        con.prepareStatement(
            "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?"
        ).use { ps ->
            ps.setString(1, tabla)
            ps.setString(2, columna)
            ps.executeQuery().use { rs -> rs.next() && rs.getInt(1) > 0 }
        }

    // Crea un usuario de demostración (si no existe) y lo enlaza a una cuenta ya existente.
    private fun crearUsuarioDemo(con: Connection, usuario: String, nombre: String, cuenta: String) {
        val existe = con.prepareStatement("SELECT 1 FROM usuarios WHERE usuario = ?").use { ps ->
            ps.setString(1, usuario)
            ps.executeQuery().use { it.next() }
        }
        if (existe) return
        val sal = Seguridad.nuevaSal()
        val id = con.prepareStatement(
            "INSERT INTO usuarios (usuario, nombre, password_hash, sal) VALUES (?, ?, ?, ?)",
            Statement.RETURN_GENERATED_KEYS
        ).use { ps ->
            ps.setString(1, usuario)
            ps.setString(2, nombre)
            ps.setString(3, Seguridad.hashear(PASSWORD_DEMO, sal))
            ps.setString(4, sal)
            ps.executeUpdate()
            ps.generatedKeys.use { it.next(); it.getLong(1) }
        }
        con.prepareStatement("UPDATE cuentas SET usuario_id = ? WHERE numero = ?").use { ps ->
            ps.setLong(1, id)
            ps.setString(2, cuenta)
            ps.executeUpdate()
        }
        log.info("Usuario de demostración creado: $usuario -> cuenta $cuenta")
    }

    // Ejecuta un bloque como TRANSACCIÓN SQL: o se guarda TODO, o no se guarda NADA.
    fun <T> transaccion(bloque: (Connection) -> T): T {
        pool.connection.use { con ->
            con.autoCommit = false
            try {
                val resultado = bloque(con)
                con.commit()                 // Todo salió bien: se guarda de forma definitiva.
                return resultado
            } catch (e: Exception) {
                con.rollback()               // Algo falló: se deshace TODO lo del bloque.
                throw e
            }
        }
    }
}
