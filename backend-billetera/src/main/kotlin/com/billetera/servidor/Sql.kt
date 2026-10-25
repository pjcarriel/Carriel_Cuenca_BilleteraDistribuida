// =====================================================================
//   Son PIEZAS pequeñas de base de datos que se repiten en varias
//   operaciones: leer saldo, guardar saldo, guardar un comprobante, etc.
// =====================================================================

package com.billetera.servidor

import java.math.BigDecimal
import java.math.RoundingMode
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Statement

// Columnas que se leen de una factura (la fecha de pago, en milisegundos).
internal const val COLUMNAS_FACTURA =
    "codigo, servicio, titular, monto, pagado, cedula, periodo, pagado_por, " +
        "CAST(UNIX_TIMESTAMP(fecha_pago) * 1000 AS SIGNED) AS fecha_pago_ms"

// Convierte una fila de la base de datos en una factura. Si no está pagada, la fecha queda vacía.
internal fun leerFactura(rs: ResultSet): ContratoRespuesta {
    val fechaPago = rs.getLong("fecha_pago_ms").takeUnless { rs.wasNull() }
    return ContratoRespuesta(
        codigo = rs.getString("codigo"),
        servicio = rs.getString("servicio"),
        titular = rs.getString("titular"),
        monto = rs.getBigDecimal("monto").toDouble(),
        pagado = rs.getBoolean("pagado"),
        cedula = rs.getString("cedula"),
        periodo = rs.getString("periodo"),
        pagadoPor = rs.getString("pagado_por"),
        fechaPago = fechaPago
    )
}

// Devuelve los códigos de servicios que existen (LUZ, AGUA, INTERNET...).
internal fun codigosServicio(con: Connection): Set<String> =
    con.prepareStatement("SELECT codigo FROM servicios").use { ps ->
        ps.executeQuery().use { rs -> buildSet { while (rs.next()) add(rs.getString(1)) } }
    }

// Revisa que el monto sea mayor a 0 y lo deja con 2 decimales exactos.
internal fun validarMonto(monto: Double): BigDecimal {
    if (monto <= 0.0 || monto.isNaN()) throw montoInvalido()
    return BigDecimal.valueOf(monto).setScale(2, RoundingMode.HALF_UP)
}

// Lee el saldo y "aparta" la cuenta: nadie más la toca hasta terminar.
// Así dos retiros al mismo tiempo no pueden sacar más dinero del que hay.
internal fun bloquearCuenta(con: Connection, numero: String): BigDecimal =
    con.prepareStatement("SELECT saldo FROM cuentas WHERE numero = ? FOR UPDATE").use { ps ->
        ps.setString(1, numero)
        ps.executeQuery().use { rs ->
            if (!rs.next()) throw cuentaNoExiste(numero)
            rs.getBigDecimal("saldo")
        }
    }

// Devuelve el nombre del dueño de una cuenta (error 404 si no existe).
internal fun titularDe(con: Connection, numero: String): String =
    con.prepareStatement("SELECT titular FROM cuentas WHERE numero = ?").use { ps ->
        ps.setString(1, numero)
        ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else throw cuentaNoExiste(numero) }
    }

// Guarda el nuevo saldo de una cuenta.
internal fun actualizarSaldo(con: Connection, numero: String, saldo: BigDecimal) {
    con.prepareStatement("UPDATE cuentas SET saldo = ? WHERE numero = ?").use { ps ->
        ps.setBigDecimal(1, saldo)
        ps.setString(2, numero)
        ps.executeUpdate()
    }
}

// Guarda un comprobante y devuelve su número. La fecha la pone MySQL.
internal fun registrarTransaccion(
    con: Connection, tipo: String, origen: String, destino: String?, monto: BigDecimal, referencia: String
): Long =
    con.prepareStatement(
        "INSERT INTO transacciones (tipo, cuenta_origen, cuenta_destino, monto, referencia) VALUES (?, ?, ?, ?, ?)",
        Statement.RETURN_GENERATED_KEYS
    ).use { ps ->
        ps.setString(1, tipo)
        ps.setString(2, origen)
        ps.setString(3, destino)
        ps.setBigDecimal(4, monto)
        ps.setString(5, referencia)
        ps.executeUpdate()
        ps.generatedKeys.use { claves -> claves.next(); claves.getLong(1) }
    }
