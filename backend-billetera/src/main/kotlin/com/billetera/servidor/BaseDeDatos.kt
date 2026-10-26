// =====================================================================
//   Es la CONEXIÓN con MySQL: crea las tablas, carga los datos de prueba
//   y ofrece "transaccion" para guardar cambios de forma segura.
// =====================================================================

package com.billetera.servidor

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.slf4j.LoggerFactory
import java.io.File
import java.sql.Connection
import java.sql.Statement
import java.util.Properties

private val log = LoggerFactory.getLogger("BaseDeDatos")

// Contraseña de los usuarios de prueba.
const val PASSWORD_DEMO = "demo123"

// Cédulas inventadas de los usuarios de prueba (pasan la validación, pero no son reales).
const val CEDULA_PAMELA = "1712345675"
const val CEDULA_USUARIO_A = "0901234567"
const val CEDULA_USUARIO_B = "0102030400"

// Acceso a MySQL (una sola copia para todo el servidor).
object BaseDeDatos {

    // Grupo de conexiones abiertas que se "prestan" a cada petición.
    private lateinit var pool: HikariDataSource

    // Lee los datos de MySQL, se conecta y prepara las tablas.
    // En la nube (Render) vienen de las variables DB_URL, DB_USER y DB_PASSWORD; en la PC, de db.properties.
    fun iniciar() {
        val urlNube = System.getenv("DB_URL")
        val props = Properties()
        if (urlNube != null) {
            props.setProperty("db.url", urlNube)
            props.setProperty("db.user", System.getenv("DB_USER"))
            props.setProperty("db.password", System.getenv("DB_PASSWORD"))
        } else {
            val archivo = File("db.properties")
            if (!archivo.exists()) {
                error("No existe db.properties. Copia db.properties.example como db.properties y pon tus datos de MySQL.")
            }
            archivo.inputStream().use { props.load(it) }
        }
        val config = HikariConfig().apply {
            jdbcUrl = props.getProperty("db.url")
            username = props.getProperty("db.user")
            password = props.getProperty("db.password")
            maximumPoolSize = 10            // Hasta 10 operaciones a la vez.
        }
        pool = HikariDataSource(config)
        log.info("Conectado a MySQL: ${config.jdbcUrl.substringBefore("?")}")
        crearTablas()
    }

    // Crea las tablas que falten y mete los datos de prueba (solo la primera vez).
    private fun crearTablas() {
        pool.connection.use { con ->
            con.createStatement().use { st ->
                // USUARIOS: se guarda la contraseña revuelta (hash), nunca la real.
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
                // CUENTAS: el saldo de cada cuenta (nunca negativo).
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
                // Si la base es antigua y le falta usuario_id, se le agrega.
                if (!existeColumna(con, "cuentas", "usuario_id")) {
                    st.executeUpdate("ALTER TABLE cuentas ADD COLUMN usuario_id BIGINT NULL UNIQUE")
                    log.info("Migración: columna cuentas.usuario_id agregada")
                }
                // TRANSACCIONES: el historial de todas las operaciones (comprobantes).
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
                // SESIONES: los tokens activos y cuándo se usaron por última vez.
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
                // CONTRATOS: las facturas de servicios básicos.
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
                // Se agrega a las facturas la cédula y el mes, para buscarlas por cédula.
                if (!existeColumna(con, "contratos", "cedula")) {
                    st.executeUpdate("ALTER TABLE contratos ADD COLUMN cedula VARCHAR(10) NULL, ADD COLUMN periodo VARCHAR(20) NULL")
                    st.executeUpdate("CREATE INDEX idx_contratos_cedula ON contratos (cedula)")
                    log.info("Migración: columnas contratos.cedula y contratos.periodo agregadas")
                }
                // SERVICIOS: catálogo de servicios; el laboratorio puede agregar más.
                st.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS servicios (
                        codigo VARCHAR(10)  PRIMARY KEY,
                        nombre VARCHAR(40)  NOT NULL,
                        icono  VARCHAR(20)  NOT NULL,
                        creado TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                    )
                    """.trimIndent()
                )
                // Los 3 servicios iniciales (si ya existen, no hace nada).
                st.executeUpdate(
                    """
                    INSERT IGNORE INTO servicios (codigo, nombre, icono) VALUES
                        ('LUZ', 'Luz eléctrica', 'RAYO'),
                        ('AGUA', 'Agua potable', 'GOTA'),
                        ('INTERNET', 'Internet', 'WIFI')
                    """.trimIndent()
                )
                // Cuentas de prueba. La 9999 (laboratorio) tiene mucho saldo para la demo.
                st.executeUpdate(
                    """
                    INSERT IGNORE INTO cuentas (numero, titular, saldo) VALUES
                        ('1001', 'Pamela Carriel', 1250.75),
                        ('1002', 'Usuario Postman A', 500.00),
                        ('1003', 'Usuario Postman B', 300.00),
                        ('9999', 'Laboratorio de Pruebas', 100000.00)
                    """.trimIndent()
                )
                // Facturas de ejemplo sin pagar.
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
                // Cédula y mes de las facturas de ejemplo (solo si aún no los tienen).
                val datosDemo = listOf(
                    Triple("445566", CEDULA_PAMELA, "Septiembre 2026"), Triple("445567", CEDULA_PAMELA, "Octubre 2026"),
                    Triple("778899", CEDULA_PAMELA, "Septiembre 2026"), Triple("778900", CEDULA_PAMELA, "Octubre 2026"),
                    Triple("NET2026", CEDULA_PAMELA, "Septiembre 2026"), Triple("NET2027", CEDULA_PAMELA, "Octubre 2026"),
                    Triple("112233", CEDULA_USUARIO_A, "Octubre 2026"), Triple("NET3001", CEDULA_USUARIO_A, "Octubre 2026"),
                    Triple("334455", CEDULA_USUARIO_B, "Octubre 2026")
                )
                con.prepareStatement("UPDATE contratos SET cedula = ?, periodo = ? WHERE codigo = ? AND cedula IS NULL").use { ps ->
                    datosDemo.forEach { (codigo, cedula, periodo) ->
                        ps.setString(1, cedula); ps.setString(2, periodo); ps.setString(3, codigo)
                        ps.addBatch()
                    }
                    ps.executeBatch()
                }
                // Facturas de noviembre sin pagar, para tener algo que pagar en la demo.
                st.executeUpdate(
                    """
                    INSERT IGNORE INTO contratos (codigo, servicio, titular, monto, cedula, periodo) VALUES
                        ('445568', 'LUZ', 'Pamela Carriel', 30.40, '$CEDULA_PAMELA', 'Noviembre 2026'),
                        ('778901', 'AGUA', 'Pamela Carriel', 19.80, '$CEDULA_PAMELA', 'Noviembre 2026'),
                        ('NET2028', 'INTERNET', 'Pamela Carriel', 25.00, '$CEDULA_PAMELA', 'Noviembre 2026'),
                        ('112234', 'LUZ', 'Usuario Postman A', 38.90, '$CEDULA_USUARIO_A', 'Noviembre 2026'),
                        ('334456', 'AGUA', 'Usuario Postman B', 16.20, '$CEDULA_USUARIO_B', 'Noviembre 2026')
                    """.trimIndent()
                )
            }
            // Usuarios de prueba, cada uno unido a su cuenta.
            crearUsuarioDemo(con, "pamela", "Pamela Carriel", "1001")
            crearUsuarioDemo(con, "usuarioa", "Usuario Postman A", "1002")
            crearUsuarioDemo(con, "usuariob", "Usuario Postman B", "1003")
            crearUsuarioDemo(con, "laboratorio", "Laboratorio de Pruebas", "9999")
        }
        log.info("Tablas listas (usuarios, cuentas, transacciones, sesiones, contratos)")
    }

    // ¿La tabla ya tiene esta columna? Devuelve true o false.
    private fun existeColumna(con: Connection, tabla: String, columna: String): Boolean =
        con.prepareStatement(
            "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?"
        ).use { ps ->
            ps.setString(1, tabla)
            ps.setString(2, columna)
            ps.executeQuery().use { rs -> rs.next() && rs.getInt(1) > 0 }
        }

    // Crea un usuario de prueba (si no existe) y lo une a su cuenta.
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

    // TRANSACCIÓN: varios cambios como uno solo; se guardan TODOS o NINGUNO.
    // Ej.: si una transferencia falla a la mitad, se deshace todo y no se pierde dinero.
    fun <T> transaccion(bloque: (Connection) -> T): T {
        pool.connection.use { con ->
            con.autoCommit = false           // No guardar cada cambio por separado.
            try {
                val resultado = bloque(con)
                con.commit()                 // Todo bien: se guarda.
                return resultado
            } catch (e: Exception) {
                con.rollback()               // Algo falló: se deshace todo.
                throw e
            }
        }
    }
}
