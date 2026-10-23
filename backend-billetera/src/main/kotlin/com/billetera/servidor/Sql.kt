// Paquete del servidor.
package com.billetera.servidor

import java.math.BigDecimal
import java.math.RoundingMode
import java.sql.Connection
import java.sql.Statement

// OPERACIONES SQL COMPARTIDAS por los repositorios (siempre dentro de una transacción).
// "internal" = visibles en todo el módulo del servidor, pero no fuera de él.

// Monto válido (> 0) convertido a BigDecimal con 2 decimales exactos.
// BigDecimal guarda el número EXACTO (un Double puede dar 0.1 + 0.2 = 0.30000000000000004).
// HALF_UP = redondeo "de toda la vida": 10.125 -> 10.13. isNaN() rechaza el valor "no es un número".
internal fun validarMonto(monto: Double): BigDecimal {
    if (monto <= 0.0 || monto.isNaN()) throw montoInvalido()
    return BigDecimal.valueOf(monto).setScale(2, RoundingMode.HALF_UP)
}

// Lee el saldo de una cuenta y BLOQUEA su fila hasta que termine la transacción ("FOR UPDATE").
// Si otra petición quiere tocar la misma cuenta, espera su turno: evita condiciones de carrera.
// Ejemplo sin bloqueo: dos retiros leen saldo 100 a la vez, cada uno resta 80 y ambos guardan 20
// (se sacaron 160 de una cuenta con 100). Con FOR UPDATE el segundo lee 20 y recibe 409.
// Solo funciona DENTRO de una transacción (autoCommit = false): ver BaseDeDatos.transaccion.
internal fun bloquearCuenta(con: Connection, numero: String): BigDecimal =
    con.prepareStatement("SELECT saldo FROM cuentas WHERE numero = ? FOR UPDATE").use { ps ->
        ps.setString(1, numero)
        ps.executeQuery().use { rs ->
            if (!rs.next()) throw cuentaNoExiste(numero)
            rs.getBigDecimal("saldo")
        }
    }

// Titular de una cuenta (sin bloquear). Lanza 404 si no existe.
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

// Inserta un comprobante en "transacciones" y devuelve su id autogenerado.
// "destino: String?" puede ser null (depósitos, retiros y pagos no tienen cuenta destino): se guarda NULL.
// La fecha no se envía: MySQL pone CURRENT_TIMESTAMP(3) por defecto.
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
