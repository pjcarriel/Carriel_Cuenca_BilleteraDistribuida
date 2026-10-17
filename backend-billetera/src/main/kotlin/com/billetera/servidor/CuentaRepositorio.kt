// Paquete del servidor.
package com.billetera.servidor

import kotlinx.coroutines.Dispatchers          // Dispatchers.IO: hilos para operaciones de disco/red.
import kotlinx.coroutines.withContext          // Ejecuta un bloque en otro hilo.
import org.slf4j.LoggerFactory
import java.math.BigDecimal

private val log = LoggerFactory.getLogger("CuentaRepositorio")

// Servicios básicos permitidos.
private val SERVICIOS_VALIDOS = setOf("LUZ", "AGUA", "INTERNET")

// Categoría que la app muestra en los pagos de servicio.
private const val CATEGORIA_SERVICIO = "Servicio Básico"

// LÓGICA DE NEGOCIO del dinero. La cuenta de ORIGEN siempre llega desde la SESIÓN (el token),
// nunca desde el JSON: así nadie puede mover dinero de una cuenta que no es suya.
// Cada función es "suspend" y salta a Dispatchers.IO porque JDBC bloquea el hilo mientras espera a MySQL.
object CuentaRepositorio {

    // GET /mi-cuenta
    suspend fun miCuenta(cuenta: String): CuentaRespuesta = withContext(Dispatchers.IO) {
        BaseDeDatos.transaccion { con ->
            con.prepareStatement("SELECT numero, titular, saldo FROM cuentas WHERE numero = ?").use { ps ->
                ps.setString(1, cuenta)
                ps.executeQuery().use { rs ->
                    if (!rs.next()) throw cuentaNoExiste(cuenta)
                    CuentaRespuesta(rs.getString("numero"), rs.getString("titular"), rs.getBigDecimal("saldo").toDouble())
                }
            }
        }
    }

    // GET /cuentas/{numero}: solo el TITULAR (para confirmar el destino de una transferencia).
    suspend fun destinatario(numero: String): DestinatarioRespuesta = withContext(Dispatchers.IO) {
        BaseDeDatos.transaccion { con -> DestinatarioRespuesta(numero, titularDe(con, numero)) }
    }

    // POST /depositar
    suspend fun depositar(cuenta: String, monto: Double): OperacionRespuesta = withContext(Dispatchers.IO) {
        val cantidad = validarMonto(monto)
        log.info("Depósito de $cantidad en $cuenta")
        BaseDeDatos.transaccion { con ->
            val nuevoSaldo = bloquearCuenta(con, cuenta) + cantidad
            actualizarSaldo(con, cuenta, nuevoSaldo)
            val id = registrarTransaccion(con, "DEPOSITO", cuenta, null, cantidad, "Depósito")
            OperacionRespuesta(id, "DEPOSITO", cuenta, cantidad.toDouble(), nuevoSaldo.toDouble(),
                System.currentTimeMillis(), "Depósito realizado")
        }
    }

    // POST /retirar (valida fondos suficientes -> 409).
    suspend fun retirar(cuenta: String, monto: Double): OperacionRespuesta = withContext(Dispatchers.IO) {
        val cantidad = validarMonto(monto)
        log.info("Retiro de $cantidad en $cuenta")
        BaseDeDatos.transaccion { con ->
            val saldoActual = bloquearCuenta(con, cuenta)
            if (saldoActual < cantidad) throw saldoInsuficiente(saldoActual.toDouble(), cantidad.toDouble())
            val nuevoSaldo = saldoActual - cantidad
            actualizarSaldo(con, cuenta, nuevoSaldo)
            val id = registrarTransaccion(con, "RETIRO", cuenta, null, cantidad, "Retiro")
            OperacionRespuesta(id, "RETIRO", cuenta, cantidad.toDouble(), nuevoSaldo.toDouble(),
                System.currentTimeMillis(), "Retiro realizado")
        }
    }

    // Resultado de una transferencia: la respuesta para el EMISOR y lo necesario para notificar al RECEPTOR.
    data class ResultadoTransferencia(
        val respuesta: OperacionRespuesta,
        val saldoDestino: Double,
        val titularOrigen: String
    )

    // POST /transferir: mueve dinero de "origen" (la sesión) a "destino".
    suspend fun transferir(origen: String, destino: String, monto: Double): ResultadoTransferencia =
        withContext(Dispatchers.IO) {
            val cantidad = validarMonto(monto)
            if (origen == destino) throw datoInvalido("No puedes transferirte a tu misma cuenta")
            log.info("Transferencia de $cantidad: $origen -> $destino")
            BaseDeDatos.transaccion { con ->
                // EVITAR INTERBLOQUEOS: bloqueamos siempre en el MISMO orden (alfabético).
                val saldos = listOf(origen, destino).sorted().associateWith { bloquearCuenta(con, it) }
                val saldoOrigen = saldos.getValue(origen)
                if (saldoOrigen < cantidad) throw saldoInsuficiente(saldoOrigen.toDouble(), cantidad.toDouble())
                val nuevoOrigen = saldoOrigen - cantidad
                val nuevoDestino = saldos.getValue(destino) + cantidad
                actualizarSaldo(con, origen, nuevoOrigen)
                actualizarSaldo(con, destino, nuevoDestino)
                val id = registrarTransaccion(con, "TRANSFERENCIA", origen, destino, cantidad, "Transferencia a $destino")
                val titularDestino = titularDe(con, destino)
                ResultadoTransferencia(
                    respuesta = OperacionRespuesta(id, "TRANSFERENCIA", origen, cantidad.toDouble(), nuevoOrigen.toDouble(),
                        System.currentTimeMillis(), "Transferencia enviada a $titularDestino ($destino)"),
                    saldoDestino = nuevoDestino.toDouble(),
                    titularOrigen = titularDe(con, origen)
                )
            }
        }

    // GET /contratos/{codigo}?servicio=LUZ: consulta una factura antes de pagarla.
    suspend fun consultarContrato(codigo: String, servicio: String?): ContratoRespuesta = withContext(Dispatchers.IO) {
        BaseDeDatos.transaccion { con -> leerContrato(con, codigo.trim().uppercase(), servicio, bloquear = false) }
    }

    // POST /pagar-servicio: paga la factura COMPLETA de un contrato (el monto lo define la factura).
    suspend fun pagarServicio(cuenta: String, servicio: String, codigo: String): OperacionRespuesta =
        withContext(Dispatchers.IO) {
            val codigoNormal = codigo.trim().uppercase()
            log.info("Pago de $servicio, contrato $codigoNormal, desde $cuenta")
            BaseDeDatos.transaccion { con ->
                // 1. Bloqueamos la FACTURA: si dos personas intentan pagarla a la vez, la segunda
                //    espera y luego ve "pagado = true" -> 409 (no se cobra dos veces).
                val factura = leerContrato(con, codigoNormal, servicio, bloquear = true)
                if (factura.pagado) throw facturaPagada(codigoNormal)
                val cantidad = BigDecimal.valueOf(factura.monto).setScale(2)
                // 2. Bloqueamos la cuenta y validamos fondos.
                val saldoActual = bloquearCuenta(con, cuenta)
                if (saldoActual < cantidad) throw saldoInsuficiente(saldoActual.toDouble(), cantidad.toDouble())
                val nuevoSaldo = saldoActual - cantidad
                actualizarSaldo(con, cuenta, nuevoSaldo)
                // 3. Marcamos la factura como pagada.
                con.prepareStatement("UPDATE contratos SET pagado = TRUE, pagado_por = ?, fecha_pago = NOW(3) WHERE codigo = ?").use { ps ->
                    ps.setString(1, cuenta); ps.setString(2, codigoNormal); ps.executeUpdate()
                }
                val referencia = "${factura.servicio} - Contrato $codigoNormal"
                val id = registrarTransaccion(con, "PAGO_SERVICIO", cuenta, null, cantidad, referencia)
                OperacionRespuesta(id, "PAGO_SERVICIO", cuenta, cantidad.toDouble(), nuevoSaldo.toDouble(),
                    System.currentTimeMillis(), "Pago de $referencia realizado")
            }
        }

    // GET /movimientos: últimos 100 comprobantes de la cuenta, vistos DESDE esa cuenta
    // (una transferencia es "ENVIADA" para quien la hace y "RECIBIDA" para quien la recibe).
    suspend fun movimientos(cuenta: String): List<MovimientoRespuesta> = withContext(Dispatchers.IO) {
        BaseDeDatos.transaccion { con ->
            con.prepareStatement(
                """
                SELECT id, tipo, cuenta_origen, cuenta_destino, monto, referencia,
                       CAST(UNIX_TIMESTAMP(fecha) * 1000 AS SIGNED) AS fecha_ms
                FROM transacciones
                WHERE cuenta_origen = ? OR cuenta_destino = ?
                ORDER BY fecha DESC, id DESC
                LIMIT 100
                """.trimIndent()
            ).use { ps ->
                ps.setString(1, cuenta); ps.setString(2, cuenta)
                ps.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            val origen = rs.getString("cuenta_origen")
                            val destino = rs.getString("cuenta_destino")
                            val referencia = rs.getString("referencia") ?: ""
                            // Convertimos el tipo del servidor al tipo de la app ("when" con Pair).
                            val (tipo, detalle) = when (rs.getString("tipo")) {
                                "DEPOSITO" -> "DEPOSITO" to
                                    (if (referencia == "Bono de bienvenida") referencia else "Depósito en cuenta $cuenta")
                                "RETIRO" -> "RETIRO" to "Retiro de efectivo"
                                "TRANSFERENCIA" ->
                                    if (origen == cuenta) "TRANSFERENCIA_ENVIADA" to "A la cuenta $destino"
                                    else "TRANSFERENCIA_RECIBIDA" to "De la cuenta $origen"
                                "PAGO_SERVICIO" -> "PAGO_SERVICIO" to referencia
                                else -> continue                  // Tipo desconocido: se omite.
                            }
                            add(MovimientoRespuesta(
                                transaccionId = rs.getLong("id"),
                                tipo = tipo,
                                monto = rs.getBigDecimal("monto").toDouble(),
                                fecha = rs.getLong("fecha_ms"),
                                detalle = detalle,
                                categoria = if (tipo == "PAGO_SERVICIO") CATEGORIA_SERVICIO else null
                            ))
                        }
                    }
                }
            }
        }
    }

    // Lee una factura. Valida que el servicio coincida (un contrato de LUZ no se paga como AGUA).
    private fun leerContrato(con: java.sql.Connection, codigo: String, servicio: String?, bloquear: Boolean): ContratoRespuesta {
        val servicioPedido = servicio?.trim()?.uppercase()
        if (servicioPedido != null && servicioPedido !in SERVICIOS_VALIDOS) {
            throw datoInvalido("Servicio no válido. Usa: $SERVICIOS_VALIDOS")
        }
        val sql = "SELECT codigo, servicio, titular, monto, pagado FROM contratos WHERE codigo = ?" + if (bloquear) " FOR UPDATE" else ""
        val factura = con.prepareStatement(sql).use { ps ->
            ps.setString(1, codigo)
            ps.executeQuery().use { rs ->
                if (!rs.next()) throw contratoNoExiste(codigo)
                ContratoRespuesta(rs.getString("codigo"), rs.getString("servicio"), rs.getString("titular"),
                    rs.getBigDecimal("monto").toDouble(), rs.getBoolean("pagado"))
            }
        }
        if (servicioPedido != null && factura.servicio != servicioPedido) {
            throw datoInvalido("El contrato $codigo es de ${factura.servicio}, no de $servicioPedido")
        }
        return factura
    }
}
