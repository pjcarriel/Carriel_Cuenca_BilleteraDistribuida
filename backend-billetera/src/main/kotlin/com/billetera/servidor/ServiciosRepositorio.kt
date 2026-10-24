// Paquete del servidor.
package com.billetera.servidor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.math.RoundingMode
import java.sql.SQLIntegrityConstraintViolationException     // MySQL: se repitió una llave primaria.
import kotlin.random.Random

private val log = LoggerFactory.getLogger("ServiciosRepositorio")

// Servicios básicos que existen (los mismos que en CuentaRepositorio y en la app).
private val SERVICIOS = setOf("LUZ", "AGUA", "INTERNET")

// Prefijo del código de contrato de cada servicio para las facturas NUEVAS (ej. "LZ482913").
private val PREFIJOS = mapOf("LUZ" to "LZ", "AGUA" to "AG", "INTERNET" to "IN")

// FACTURAS DE SERVICIOS BÁSICOS (Fase 8):
//  - Cualquier usuario puede BUSCAR las facturas de una cédula (como en la ventanilla de un banco).
//  - Solo el LABORATORIO (la "empresa de servicios") puede listar TODAS y CREAR facturas nuevas.
// Igual que los demás repositorios: JDBC bloquea el hilo, así que todo corre en Dispatchers.IO.
object ServiciosRepositorio {

    // GET /facturas?cedula=...&servicio=...  -> facturas de esa cédula y ese servicio.
    // Primero las PENDIENTES (pagado = FALSE va antes que TRUE) y luego por código.
    suspend fun porCedula(cedula: String, servicio: String): List<ContratoRespuesta> = withContext(Dispatchers.IO) {
        val ced = cedula.trim()
        val serv = servicio.trim().uppercase()
        if (!cedulaValida(ced)) throw cedulaInvalida()
        if (serv !in SERVICIOS) throw datoInvalido("Servicio no válido. Usa: $SERVICIOS")
        BaseDeDatos.transaccion { con ->
            con.prepareStatement(
                "SELECT $COLUMNAS_FACTURA FROM contratos WHERE cedula = ? AND servicio = ? ORDER BY pagado, codigo DESC"
            ).use { ps ->
                ps.setString(1, ced); ps.setString(2, serv)
                ps.executeQuery().use { rs -> buildList { while (rs.next()) add(leerFactura(rs)) } }
            }
        }
    }

    // GET /admin/facturas?estado=PENDIENTES|PAGADAS|TODAS&servicio=LUZ  (solo laboratorio).
    // La consulta se ARMA según los filtros, pero los valores siempre van con "?" (sin inyección SQL).
    suspend fun listar(estado: String?, servicio: String?): List<ContratoRespuesta> = withContext(Dispatchers.IO) {
        val condiciones = mutableListOf<String>()
        val valores = mutableListOf<String>()
        when (estado?.uppercase()) {
            "PENDIENTES" -> condiciones += "pagado = FALSE"
            "PAGADAS" -> condiciones += "pagado = TRUE"
        }
        servicio?.trim()?.uppercase()?.takeIf { it in SERVICIOS }?.let { condiciones += "servicio = ?"; valores += it }
        val where = if (condiciones.isEmpty()) "" else "WHERE " + condiciones.joinToString(" AND ")
        BaseDeDatos.transaccion { con ->
            // Las pagadas más recientes primero; las pendientes después, por código.
            con.prepareStatement("SELECT $COLUMNAS_FACTURA FROM contratos $where ORDER BY pagado, fecha_pago DESC, codigo DESC").use { ps ->
                valores.forEachIndexed { i, v -> ps.setString(i + 1, v) }
                ps.executeQuery().use { rs -> buildList { while (rs.next()) add(leerFactura(rs)) } }
            }
        }
    }

    // POST /admin/facturas (solo laboratorio): crea una factura PENDIENTE con un código nuevo.
    suspend fun crear(req: NuevaFacturaRequest): ContratoRespuesta = withContext(Dispatchers.IO) {
        val cedula = req.cedula.trim()
        val titular = req.titular.trim()
        val servicio = req.servicio.trim().uppercase()
        val periodo = req.periodo.trim()
        if (!cedulaValida(cedula)) throw cedulaInvalida()
        if (servicio !in SERVICIOS) throw datoInvalido("Servicio no válido. Usa: $SERVICIOS")
        if (titular.length !in 3..100) throw datoInvalido("Escribe el nombre del titular (3 a 100 caracteres)")
        if (periodo.length !in 3..20) throw datoInvalido("Escribe el periodo (ej. Diciembre 2026)")
        val monto = validarMonto(req.monto).setScale(2, RoundingMode.HALF_UP)
        // El código es el prefijo del servicio + 6 dígitos al azar. Si por casualidad ya existe
        // (la llave primaria lo rechaza), se intenta con otro número (hasta 5 veces).
        repeat(5) {
            val codigo = PREFIJOS.getValue(servicio) + Random.nextInt(100_000, 1_000_000)
            try {
                BaseDeDatos.transaccion { con ->
                    con.prepareStatement(
                        "INSERT INTO contratos (codigo, servicio, titular, monto, cedula, periodo) VALUES (?, ?, ?, ?, ?, ?)"
                    ).use { ps ->
                        ps.setString(1, codigo); ps.setString(2, servicio); ps.setString(3, titular)
                        ps.setBigDecimal(4, monto); ps.setString(5, cedula); ps.setString(6, periodo)
                        ps.executeUpdate()
                    }
                }
                log.info("Factura creada: $servicio $codigo de $titular ($cedula), $periodo, $monto")
                return@withContext ContratoRespuesta(codigo, servicio, titular, monto.toDouble(), false, cedula, periodo)
            } catch (e: SQLIntegrityConstraintViolationException) {
                log.info("Código $codigo repetido, se intenta con otro")
            }
        }
        throw datoInvalido("No se pudo generar un código de contrato; intenta de nuevo")
    }
}
