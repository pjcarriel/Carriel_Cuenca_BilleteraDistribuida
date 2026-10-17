// Paquete del servidor.
package com.billetera.servidor

import com.zaxxer.hikari.HikariConfig         // Configuración del pool de conexiones.
import com.zaxxer.hikari.HikariDataSource     // Pool de conexiones a MySQL.
import org.slf4j.LoggerFactory                // Para escribir logs.
import java.io.File                           // Para leer el archivo db.properties.
import java.sql.Connection                    // Una conexión a la base de datos.
import java.util.Properties                   // Lee archivos "clave=valor".

// Logger de este archivo (los mensajes muestran la hora y el HILO, ver logback.xml).
private val log = LoggerFactory.getLogger("BaseDeDatos")

// Acceso a MySQL. "object" = una sola instancia para todo el servidor.
object BaseDeDatos {

    // "lateinit" = se inicializa más tarde (en iniciar()), pero nunca será null al usarse.
    private lateinit var pool: HikariDataSource

    // Lee db.properties, abre el pool de conexiones y prepara las tablas.
    fun iniciar() {
        val archivo = File("db.properties")   // Se busca en la carpeta backend-billetera.
        // Si el archivo no existe, detenemos el servidor con instrucciones claras.
        if (!archivo.exists()) {
            error("No existe db.properties. Copia db.properties.example como db.properties y pon tus datos de MySQL.")
        }
        // Leemos las claves db.url, db.user y db.password.
        val props = Properties().apply { archivo.inputStream().use { load(it) } }

        // Configuramos el pool: mantiene varias conexiones abiertas y las presta a cada petición.
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

    // Crea las tablas si no existen y agrega cuentas de prueba (solo la primera vez).
    private fun crearTablas() {
        pool.connection.use { con ->        // "use" cierra la conexión al terminar, aunque haya error.
            con.createStatement().use { st ->
                // Tabla de cuentas. DECIMAL(12,2) = número exacto con 2 decimales (ideal para dinero).
                // CHECK (saldo >= 0): MySQL rechaza cualquier saldo negativo (segunda barrera de seguridad).
                st.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS cuentas (
                        numero  VARCHAR(20)   PRIMARY KEY,
                        titular VARCHAR(100)  NOT NULL,
                        saldo   DECIMAL(12,2) NOT NULL DEFAULT 0,
                        CHECK (saldo >= 0)
                    )
                    """.trimIndent()
                )
                // Tabla de transacciones: el historial OFICIAL del servidor.
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
                // Cuentas de prueba. INSERT IGNORE = si ya existen, no hace nada (no se duplican).
                // 1001 = la cuenta de la app; 1002 y 1003 = otros usuarios (los usaremos desde Postman).
                st.executeUpdate(
                    """
                    INSERT IGNORE INTO cuentas (numero, titular, saldo) VALUES
                        ('1001', 'Pamela Carriel', 1250.75),
                        ('1002', 'Usuario Postman A', 500.00),
                        ('1003', 'Usuario Postman B', 300.00)
                    """.trimIndent()
                )
            }
        }
        log.info("Tablas listas (cuentas, transacciones)")
    }

    // Ejecuta un bloque como TRANSACCIÓN SQL: o se guarda TODO, o no se guarda NADA.
    // Ejemplo: en una transferencia se resta a una cuenta y se suma a otra; si algo falla
    // a la mitad, el "rollback" deshace la resta y el dinero no desaparece.
    // "<T>" = genérico: el bloque puede devolver cualquier tipo de resultado.
    // "(Connection) -> T" = lambda que recibe la conexión y devuelve un T.
    fun <T> transaccion(bloque: (Connection) -> T): T {
        pool.connection.use { con ->
            con.autoCommit = false           // No guardar automáticamente cada sentencia.
            try {
                val resultado = bloque(con)  // Ejecutamos todas las operaciones del bloque.
                con.commit()                 // Todo salió bien: se guarda de forma definitiva.
                return resultado
            } catch (e: Exception) {
                con.rollback()               // Algo falló: se deshace TODO lo del bloque.
                throw e                      // Y se relanza el error para responder al cliente.
            }
        }
    }
}
