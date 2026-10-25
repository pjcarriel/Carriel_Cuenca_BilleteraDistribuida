// Paquete del servidor.
package com.billetera.servidor

import kotlinx.coroutines.Dispatchers          // Dispatchers.IO: hilos para operaciones de disco/red.
import kotlinx.coroutines.withContext          // Ejecuta un bloque en otro hilo.
import org.slf4j.LoggerFactory
import java.math.BigDecimal

private val log = LoggerFactory.getLogger("CuentaRepositorio")


// Categoría que la app muestra en los pagos de servicio.
private const val CATEGORIA_SERVICIO = "Servicio Básico"

// LÓGICA DE NEGOCIO del dinero. La cuenta de ORIGEN siempre llega desde la SESIÓN (el token),
// nunca desde el JSON: así nadie puede mover dinero de una cuenta que no es suya.
// Cada función es "suspend" y salta a Dispatchers.IO porque JDBC bloquea el hilo mientras espera a MySQL.
object CuentaRepositorio {

    // GET /mi-cuenta
    // withContext(Dispatchers.IO): la corrutina SALTA a un hilo del grupo IO mientras MySQL responde,
    // y al terminar vuelve con el resultado. Así los hilos de CIO quedan libres para atender a otros.
    // Si la cuenta no existe se lanza un ErrorNegocio, que StatusPages convierte en 404.
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
        // Leer saldo (bloqueado) + sumar + guardar + registrar comprobante: TODO en una misma transacción.
        // Si dos depósitos llegan a la vez, el segundo espera al primero y no se pierde ninguno.
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
            // Con BigDecimal, "<" y "-" funcionan igual que con números normales (Kotlin usa compareTo y minus).
            // El "throw" dentro de la transacción provoca ROLLBACK: no se guarda nada.
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
                // (Si A->B bloqueara A y luego B, y a la vez B->A bloqueara B y luego A, cada una
                // esperaría a la otra para siempre: eso es un DEADLOCK.)
                // associateWith crea un mapa cuenta -> saldo bloqueado, ej. {"1001"=500.00, "1002"=80.00}.
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
    // Solo LEE la factura: bloquear = false (no hace falta reservarla porque no se modifica).
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
                // setScale(2) = el monto con exactamente 2 decimales, como en la columna DECIMAL(12,2).
                val cantidad = BigDecimal.valueOf(factura.monto).setScale(2)
                // 2. Bloqueamos la cuenta y validamos fondos.
                val saldoActual = bloquearCuenta(con, cuenta)
                if (saldoActual < cantidad) throw saldoInsuficiente(saldoActual.toDouble(), cantidad.toDouble())
                val nuevoSaldo = saldoActual - cantidad
                actualizarSaldo(con, cuenta, nuevoSaldo)
                // 3. Marcamos la factura como pagada. NOW(3) = fecha y hora actual de MySQL con milisegundos.
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
            // CONSULTA: comprobantes donde la cuenta es ORIGEN o DESTINO (enviados y recibidos).
            //  - UNIX_TIMESTAMP(fecha) * 1000 -> la fecha en MILISEGUNDOS desde 1970 (el formato que usa la app);
            //    CAST(... AS SIGNED) lo deja como número entero.
            //  - ORDER BY fecha DESC, id DESC -> los más recientes primero (id desempata si la fecha es igual).
            //  - LIMIT 100 -> como máximo 100 filas.
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
                    // buildList { add(...) } arma una lista; "while (rs.next())" recorre las filas una por una.
                    buildList {
                        while (rs.next()) {
                            val origen = rs.getString("cuenta_origen")
                            val destino = rs.getString("cuenta_destino")
                            val referencia = rs.getString("referencia") ?: ""
                            // Convertimos el tipo del servidor al tipo de la app ("when" con Pair).
                            // "a to b" crea un Pair(a, b); "val (tipo, detalle) =" lo separa en dos variables.
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
        // Los servicios válidos salen de la tabla servicios (el laboratorio puede agregar más).
        val validos = codigosServicio(con)
        if (servicioPedido != null && servicioPedido !in validos) {
            throw datoInvalido("Servicio no válido. Usa: $validos")
        }
        // Solo al PAGAR se agrega " FOR UPDATE": bloquea la fila de la factura hasta el fin de la transacción.
        val sql = "SELECT $COLUMNAS_FACTURA FROM contratos WHERE codigo = ?" + if (bloquear) " FOR UPDATE" else ""
        val factura = con.prepareStatement(sql).use { ps ->
            ps.setString(1, codigo)
            ps.executeQuery().use { rs ->
                if (!rs.next()) throw contratoNoExiste(codigo)
                leerFactura(rs)                                 // Fila -> ContratoRespuesta (Sql.kt).
            }
        }
        if (servicioPedido != null && factura.servicio != servicioPedido) {
            throw datoInvalido("El contrato $codigo es de ${factura.servicio}, no de $servicioPedido")
        }
        return factura
    }
}
