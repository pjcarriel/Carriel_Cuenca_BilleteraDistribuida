// =====================================================================
//   Aquí están las operaciones con DINERO: ver saldo, depositar, retirar,
//   transferir, pagar servicios y ver el historial de movimientos.
// =====================================================================

package com.billetera.servidor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.math.BigDecimal

private val log = LoggerFactory.getLogger("CuentaRepositorio")


// Categoría que la app muestra en los pagos de servicio.
private const val CATEGORIA_SERVICIO = "Servicio Básico"

// Operaciones con dinero. La cuenta de origen siempre sale de la sesión, nunca de la app.
object CuentaRepositorio {

    // Devuelve número, dueño y saldo de la cuenta del usuario.
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

    // Devuelve el nombre del dueño de otra cuenta, para confirmar a quién se transfiere.
    suspend fun destinatario(numero: String): DestinatarioRespuesta = withContext(Dispatchers.IO) {
        BaseDeDatos.transaccion { con -> DestinatarioRespuesta(numero, titularDe(con, numero)) }
    }

    // DEPÓSITO: suma dinero a la cuenta y guarda el comprobante.
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

    // RETIRO: resta dinero; si no alcanza el saldo, error 409 y no se guarda nada.
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

    // Resultado de una transferencia: respuesta para quien envía y datos para avisar a quien recibe.
    data class ResultadoTransferencia(
        val respuesta: OperacionRespuesta,
        val saldoDestino: Double,
        val titularOrigen: String
    )

    // TRANSFERENCIA: pasa dinero de la cuenta del usuario a otra cuenta.
    suspend fun transferir(origen: String, destino: String, monto: Double): ResultadoTransferencia =
        withContext(Dispatchers.IO) {
            val cantidad = validarMonto(monto)
            if (origen == destino) throw datoInvalido("No puedes transferirte a tu misma cuenta")
            log.info("Transferencia de $cantidad: $origen -> $destino")
            BaseDeDatos.transaccion { con ->
                // Se apartan las dos cuentas siempre en el mismo orden, para que
                // dos transferencias cruzadas no se queden esperándose para siempre.
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

    // Muestra una factura antes de pagarla.
    suspend fun consultarContrato(codigo: String, servicio: String?): ContratoRespuesta = withContext(Dispatchers.IO) {
        BaseDeDatos.transaccion { con -> leerContrato(con, codigo.trim().uppercase(), servicio, bloquear = false) }
    }

    // PAGO DE SERVICIO: paga la factura completa (el monto lo dice la factura).
    suspend fun pagarServicio(cuenta: String, servicio: String, codigo: String): OperacionRespuesta =
        withContext(Dispatchers.IO) {
            val codigoNormal = codigo.trim().uppercase()
            log.info("Pago de $servicio, contrato $codigoNormal, desde $cuenta")
            BaseDeDatos.transaccion { con ->
                // 1. Apartar la factura: así no se puede pagar dos veces.
                val factura = leerContrato(con, codigoNormal, servicio, bloquear = true)
                if (factura.pagado) throw facturaPagada(codigoNormal)
                val cantidad = BigDecimal.valueOf(factura.monto).setScale(2)
                // 2. Apartar la cuenta y revisar el saldo.
                val saldoActual = bloquearCuenta(con, cuenta)
                if (saldoActual < cantidad) throw saldoInsuficiente(saldoActual.toDouble(), cantidad.toDouble())
                val nuevoSaldo = saldoActual - cantidad
                actualizarSaldo(con, cuenta, nuevoSaldo)
                // 3. Marcar la factura como pagada.
                con.prepareStatement("UPDATE contratos SET pagado = TRUE, pagado_por = ?, fecha_pago = NOW(3) WHERE codigo = ?").use { ps ->
                    ps.setString(1, cuenta); ps.setString(2, codigoNormal); ps.executeUpdate()
                }
                // 4. Guardar el comprobante.
                val referencia = "${factura.servicio} - Contrato $codigoNormal"
                val id = registrarTransaccion(con, "PAGO_SERVICIO", cuenta, null, cantidad, referencia)
                OperacionRespuesta(id, "PAGO_SERVICIO", cuenta, cantidad.toDouble(), nuevoSaldo.toDouble(),
                    System.currentTimeMillis(), "Pago de $referencia realizado")
            }
        }

    // HISTORIAL: los últimos 100 movimientos de la cuenta, del más nuevo al más viejo.
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
                            // Traduce el tipo al que entiende la app, con su texto de detalle.
                            val (tipo, detalle) = when (rs.getString("tipo")) {
                                "DEPOSITO" -> "DEPOSITO" to
                                    (if (referencia == "Bono de bienvenida") referencia else "Depósito en cuenta $cuenta")
                                "RETIRO" -> "RETIRO" to "Retiro de efectivo"
                                "TRANSFERENCIA" ->
                                    if (origen == cuenta) "TRANSFERENCIA_ENVIADA" to "A la cuenta $destino"
                                    else "TRANSFERENCIA_RECIBIDA" to "De la cuenta $origen"
                                "PAGO_SERVICIO" -> "PAGO_SERVICIO" to referencia
                                else -> continue
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

    // Busca una factura y revisa que sea del servicio correcto. Si "bloquear", la aparta.
    private fun leerContrato(con: java.sql.Connection, codigo: String, servicio: String?, bloquear: Boolean): ContratoRespuesta {
        val servicioPedido = servicio?.trim()?.uppercase()
        val validos = codigosServicio(con)
        if (servicioPedido != null && servicioPedido !in validos) {
            throw datoInvalido("Servicio no válido. Usa: $validos")
        }
        val sql = "SELECT $COLUMNAS_FACTURA FROM contratos WHERE codigo = ?" + if (bloquear) " FOR UPDATE" else ""
        val factura = con.prepareStatement(sql).use { ps ->
            ps.setString(1, codigo)
            ps.executeQuery().use { rs ->
                if (!rs.next()) throw contratoNoExiste(codigo)
                leerFactura(rs)
            }
        }
        if (servicioPedido != null && factura.servicio != servicioPedido) {
            throw datoInvalido("El contrato $codigo es de ${factura.servicio}, no de $servicioPedido")
        }
        return factura
    }
}
