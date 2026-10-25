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

// Cédulas FICTICIAS de los titulares de demostración (válidas según el algoritmo, ver Cedula.kt).
const val CEDULA_PAMELA = "1712345675"
const val CEDULA_USUARIO_A = "0901234567"
const val CEDULA_USUARIO_B = "0102030400"

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
        // "apply { }" = configura el objeto recién creado y lo devuelve.
        // "use { }" = abre el archivo, lo usa y lo CIERRA solo al terminar (aunque ocurra un error).
        val props = Properties().apply { archivo.inputStream().use { load(it) } }
        // POOL DE CONEXIONES (HikariCP): abrir una conexión a MySQL es lento, así que el pool deja
        // varias abiertas y las PRESTA a cada petición; al terminar, la conexión vuelve al pool.
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
    // "CREATE TABLE IF NOT EXISTS" = crea la tabla solo si no existe: se puede reiniciar el servidor sin perder datos.
    private fun crearTablas() {
        // pool.connection pide prestada una conexión; "use" la devuelve al pool al terminar.
        // createStatement() sirve para SQL fijo (sin datos del usuario); para datos usamos prepareStatement con "?".
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
                // DECIMAL(12,2) = número EXACTO con 2 decimales (el dinero no se guarda como Double, que redondea).
                // usuario_id UNIQUE = cada usuario tiene como máximo UNA cuenta.
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
                // FOREIGN KEY = cuenta_origen debe existir en "cuentas" (MySQL no deja guardar comprobantes huérfanos).
                // TIMESTAMP(3) = fecha y hora con milisegundos.
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
                // MIGRACIÓN (Fase 8): cada factura sabe de QUÉ CÉDULA es y de QUÉ MES (periodo).
                // Así se pueden buscar las facturas de una persona por su cédula.
                if (!existeColumna(con, "contratos", "cedula")) {
                    st.executeUpdate("ALTER TABLE contratos ADD COLUMN cedula VARCHAR(10) NULL, ADD COLUMN periodo VARCHAR(20) NULL")
                    // Índice: la búsqueda por cédula no tiene que recorrer toda la tabla.
                    st.executeUpdate("CREATE INDEX idx_contratos_cedula ON contratos (cedula)")
                    log.info("Migración: columnas contratos.cedula y contratos.periodo agregadas")
                }
                // SERVICIOS: catálogo de servicios básicos que se pueden facturar y pagar.
                // Antes eran fijos en el código (LUZ, AGUA, INTERNET); ahora el laboratorio puede AGREGAR más.
                // icono = clave del ícono que dibuja la app (RAYO, GOTA, WIFI, TELEFONO, GAS, TV, BASURA, OTRO).
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
                st.executeUpdate(
                    """
                    INSERT IGNORE INTO servicios (codigo, nombre, icono) VALUES
                        ('LUZ', 'Luz eléctrica', 'RAYO'),
                        ('AGUA', 'Agua potable', 'GOTA'),
                        ('INTERNET', 'Internet', 'WIFI')
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
                // Cédula y mes de las facturas de ejemplo (solo si todavía no tienen: "cedula IS NULL").
                // Las cédulas son FICTICIAS: cumplen el algoritmo de validación, pero no son de personas reales.
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
                        ps.addBatch()                           // Se acumulan y se envían juntas (executeBatch).
                    }
                    ps.executeBatch()
                }
                // Facturas NUEVAS de noviembre (pendientes), para que siempre haya algo que pagar en la demo.
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
            // Usuarios de demostración, enlazados a las cuentas 1001, 1002 y 1003.
            crearUsuarioDemo(con, "pamela", "Pamela Carriel", "1001")
            crearUsuarioDemo(con, "usuarioa", "Usuario Postman A", "1002")
            crearUsuarioDemo(con, "usuariob", "Usuario Postman B", "1003")
            crearUsuarioDemo(con, "laboratorio", "Laboratorio de Pruebas", "9999")
        }
        log.info("Tablas listas (usuarios, cuentas, transacciones, sesiones, contratos)")
    }

    // ¿Existe una columna en una tabla? (consulta el catálogo de MySQL: information_schema).
    // PreparedStatement: cada "?" se reemplaza de forma SEGURA con setString(posición, valor), empezando en 1.
    // Así un texto malicioso nunca se ejecuta como SQL (evita la "inyección SQL").
    // rs.next() avanza a la primera fila (false si no hay); rs.getInt(1) lee la primera columna (el COUNT).
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
        // "SELECT 1" solo pregunta si hay alguna fila; no nos interesan sus datos.
        val existe = con.prepareStatement("SELECT 1 FROM usuarios WHERE usuario = ?").use { ps ->
            ps.setString(1, usuario)
            ps.executeQuery().use { it.next() }
        }
        if (existe) return
        val sal = Seguridad.nuevaSal()
        // RETURN_GENERATED_KEYS: pide a MySQL que devuelva el id AUTO_INCREMENT que acaba de crear.
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
    // "<T>" = GENÉRICO: el bloque puede devolver cualquier tipo (un saldo, una respuesta...) y
    // transaccion() devuelve ese mismo tipo. "bloque" es una LAMBDA que recibe la conexión.
    // Los bloqueos "FOR UPDATE" que se hagan dentro duran hasta el commit o el rollback.
    fun <T> transaccion(bloque: (Connection) -> T): T {
        pool.connection.use { con ->
            // Por defecto JDBC guarda cada sentencia al instante (autoCommit). Lo apagamos para
            // agrupar varias sentencias y confirmarlas JUNTAS con commit().
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
