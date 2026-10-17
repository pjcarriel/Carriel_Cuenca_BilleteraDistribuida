// Paquete del servidor.
package com.billetera.servidor

import kotlinx.coroutines.Dispatchers          // Dispatchers.IO: hilos para operaciones de disco/red.
import kotlinx.coroutines.withContext          // Ejecuta un bloque en otro hilo.
import org.slf4j.LoggerFactory
import java.math.BigDecimal                    // Número decimal EXACTO (evita errores como 0.1 + 0.2 = 0.30000000004).
import java.math.RoundingMode                  // Cómo redondear a 2 decimales.
import java.sql.Connection
import java.sql.Statement

private val log = LoggerFactory.getLogger("CuentaRepositorio")

// Servicios básicos permitidos para el pago de servicios.
private val SERVICIOS_VALIDOS = setOf("LUZ", "AGUA", "INTERNET")

// Toda la LÓGICA DE NEGOCIO del dinero.
// Cada función es "suspend" y salta a Dispatchers.IO, porque JDBC (MySQL) BLOQUEA el hilo
// mientras espera la respuesta. Así los hilos de Ktor/Netty quedan libres para atender
// otras peticiones (la misma idea de concurrencia que en la app Android).
object CuentaRepositorio {

    // GET /cuentas/{numero}: consulta una cuenta.
    suspend fun consultar(numero: String): CuentaRespuesta = withContext(Dispatchers.IO) {
        BaseDeDatos.transaccion { con ->
            con.prepareStatement("SELECT numero, titular, saldo FROM cuentas WHERE numero = ?").use { ps ->
                ps.setString(1, numero)                  // Reemplaza el "?" (evita inyección SQL).
                ps.executeQuery().use { rs ->
                    if (!rs.next()) throw cuentaNoExiste(numero)
                    CuentaRespuesta(rs.getString("numero"), rs.getString("titular"), rs.getBigDecimal("saldo").toDouble())
                }
            }
        }
    }

    // POST /depositar: suma dinero a la cuenta.
    suspend fun depositar(cuenta: String, monto: Double): OperacionRespuesta = withContext(Dispatchers.IO) {
        val cantidad = validarMonto(monto)
        log.info("Depósito de $cantidad en $cuenta")
        BaseDeDatos.transaccion { con ->
            val saldoActual = bloquearCuenta(con, cuenta)                  // Lee el saldo y BLOQUEA la fila.
            val nuevoSaldo = saldoActual + cantidad                        // BigDecimal permite usar "+".
            actualizarSaldo(con, cuenta, nuevoSaldo)
            val id = registrarTransaccion(con, "DEPOSITO", cuenta, null, cantidad, "Depósito")
            OperacionRespuesta(id, "DEPOSITO", cuenta, cantidad.toDouble(), nuevoSaldo.toDouble(),
                System.currentTimeMillis(), "Depósito realizado")
        }
    }

    // POST /retirar: resta dinero VALIDANDO que haya fondos suficientes.
    suspend fun retirar(cuenta: String, monto: Double): OperacionRespuesta = withContext(Dispatchers.IO) {
        val cantidad = validarMonto(monto)
        log.info("Retiro de $cantidad en $cuenta")
        BaseDeDatos.transaccion { con ->
            val saldoActual = bloquearCuenta(con, cuenta)
            // Si no alcanza, lanzamos el error 409 (la transacción hace rollback automáticamente).
            if (saldoActual < cantidad) throw saldoInsuficiente(saldoActual.toDouble(), cantidad.toDouble())
            val nuevoSaldo = saldoActual - cantidad
            actualizarSaldo(con, cuenta, nuevoSaldo)
            val id = registrarTransaccion(con, "RETIRO", cuenta, null, cantidad, "Retiro")
            OperacionRespuesta(id, "RETIRO", cuenta, cantidad.toDouble(), nuevoSaldo.toDouble(),
                System.currentTimeMillis(), "Retiro realizado")
        }
    }

    // POST /transferir: mueve dinero de una cuenta a otra.
    // Devuelve un "Pair" (par de valores): la respuesta para el EMISOR y el saldo nuevo del RECEPTOR
    // (el saldo del receptor se usa para notificarle por WebSocket).
    suspend fun transferir(origen: String, destino: String, monto: Double): Pair<OperacionRespuesta, Double> =
        withContext(Dispatchers.IO) {
            val cantidad = validarMonto(monto)
            if (origen == destino) throw datoInvalido("No puedes transferirte a tu misma cuenta")
            log.info("Transferencia de $cantidad: $origen -> $destino")
            BaseDeDatos.transaccion { con ->
                // EVITAR INTERBLOQUEOS (deadlock): siempre bloqueamos las cuentas en el MISMO orden
                // (alfabético). Si dos transferencias cruzadas (A->B y B->A) llegan a la vez,
                // ambas piden primero la misma cuenta y una espera a la otra, en vez de trabarse.
                val saldos = listOf(origen, destino).sorted().associateWith { bloquearCuenta(con, it) }
                val saldoOrigen = saldos.getValue(origen)
                val saldoDestino = saldos.getValue(destino)

                if (saldoOrigen < cantidad) throw saldoInsuficiente(saldoOrigen.toDouble(), cantidad.toDouble())

                val nuevoOrigen = saldoOrigen - cantidad
                val nuevoDestino = saldoDestino + cantidad
                actualizarSaldo(con, origen, nuevoOrigen)
                actualizarSaldo(con, destino, nuevoDestino)
                val id = registrarTransaccion(con, "TRANSFERENCIA", origen, destino, cantidad, "Transferencia a $destino")

                // "to" crea un Pair: (respuesta del emisor) to (saldo del receptor).
                OperacionRespuesta(id, "TRANSFERENCIA", origen, cantidad.toDouble(), nuevoOrigen.toDouble(),
                    System.currentTimeMillis(), "Transferencia enviada a $destino") to nuevoDestino.toDouble()
            }
        }

    // POST /pagar-servicio: paga una factura de luz, agua o internet.
    suspend fun pagarServicio(cuenta: String, servicio: String, contrato: String, monto: Double): OperacionRespuesta =
        withContext(Dispatchers.IO) {
            val cantidad = validarMonto(monto)
            val nombreServicio = servicio.trim().uppercase()        // "luz" -> "LUZ".
            if (nombreServicio !in SERVICIOS_VALIDOS) throw datoInvalido("Servicio no válido. Usa: $SERVICIOS_VALIDOS")
            if (contrato.isBlank()) throw datoInvalido("El código de contrato es obligatorio")
            log.info("Pago de $nombreServicio (contrato $contrato) por $cantidad desde $cuenta")
            BaseDeDatos.transaccion { con ->
                val saldoActual = bloquearCuenta(con, cuenta)
                if (saldoActual < cantidad) throw saldoInsuficiente(saldoActual.toDouble(), cantidad.toDouble())
                val nuevoSaldo = saldoActual - cantidad
                actualizarSaldo(con, cuenta, nuevoSaldo)
                val referencia = "$nombreServicio - Contrato ${contrato.trim()}"
                val id = registrarTransaccion(con, "PAGO_SERVICIO", cuenta, null, cantidad, referencia)
                OperacionRespuesta(id, "PAGO_SERVICIO", cuenta, cantidad.toDouble(), nuevoSaldo.toDouble(),
                    System.currentTimeMillis(), "Pago de $referencia realizado")
            }
        }

    // ------------------------- funciones privadas de ayuda -------------------------

    // Valida que el monto sea positivo y lo convierte a BigDecimal con 2 decimales.
    private fun validarMonto(monto: Double): BigDecimal {
        if (monto <= 0.0 || monto.isNaN()) throw montoInvalido()
        return BigDecimal.valueOf(monto).setScale(2, RoundingMode.HALF_UP)
    }

    // Lee el saldo de una cuenta y BLOQUEA su fila hasta que termine la transacción.
    // "FOR UPDATE": si otra petición quiere tocar esta misma cuenta, ESPERA su turno.
    // Así dos retiros simultáneos no pueden leer el mismo saldo y dejarlo negativo
    // (problema clásico de concurrencia llamado "condición de carrera").
    private fun bloquearCuenta(con: Connection, numero: String): BigDecimal =
        con.prepareStatement("SELECT saldo FROM cuentas WHERE numero = ? FOR UPDATE").use { ps ->
            ps.setString(1, numero)
            ps.executeQuery().use { rs ->
                if (!rs.next()) throw cuentaNoExiste(numero)
                rs.getBigDecimal("saldo")
            }
        }

    // Guarda el nuevo saldo de una cuenta.
    private fun actualizarSaldo(con: Connection, numero: String, saldo: BigDecimal) {
        con.prepareStatement("UPDATE cuentas SET saldo = ? WHERE numero = ?").use { ps ->
            ps.setBigDecimal(1, saldo)
            ps.setString(2, numero)
            ps.executeUpdate()
        }
    }

    // Inserta el comprobante en la tabla transacciones y devuelve su id autogenerado.
    private fun registrarTransaccion(
        con: Connection, tipo: String, origen: String, destino: String?, monto: BigDecimal, referencia: String
    ): Long =
        con.prepareStatement(
            "INSERT INTO transacciones (tipo, cuenta_origen, cuenta_destino, monto, referencia) VALUES (?, ?, ?, ?, ?)",
            Statement.RETURN_GENERATED_KEYS     // Pedimos que MySQL nos devuelva el id generado.
        ).use { ps ->
            ps.setString(1, tipo)
            ps.setString(2, origen)
            ps.setString(3, destino)            // Puede ser null (solo las transferencias tienen destino).
            ps.setBigDecimal(4, monto)
            ps.setString(5, referencia)
            ps.executeUpdate()
            ps.generatedKeys.use { claves -> claves.next(); claves.getLong(1) }
        }
}
