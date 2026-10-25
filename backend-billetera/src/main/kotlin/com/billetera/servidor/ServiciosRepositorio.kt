// =====================================================================
//   Maneja los SERVICIOS básicos (luz, agua, internet...) y sus FACTURAS:
//   verlos, buscarlos por cédula y, solo el laboratorio, crearlos.
// =====================================================================

package com.billetera.servidor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.math.RoundingMode
import java.sql.Connection
import java.sql.SQLIntegrityConstraintViolationException
import kotlin.random.Random

private val log = LoggerFactory.getLogger("ServiciosRepositorio")

// Íconos que la app sabe dibujar.
val ICONOS_SERVICIO = setOf("RAYO", "GOTA", "WIFI", "TELEFONO", "GAS", "TV", "BASURA", "OTRO")

// Código de servicio: de 3 a 10 letras mayúsculas (ej. "GAS").
private val PATRON_CODIGO_SERVICIO = Regex("^[A-Z]{3,10}$")

// Letras con que empieza el código de una factura nueva (ej. "LZ482913").
private val PREFIJOS = mapOf("LUZ" to "LZ", "AGUA" to "AG", "INTERNET" to "IN")

// Servicios y facturas.
object ServiciosRepositorio {

    // Devuelve la lista de servicios: primero LUZ, AGUA, INTERNET y luego los nuevos.
    suspend fun servicios(): List<ServicioRespuesta> = withContext(Dispatchers.IO) {
        BaseDeDatos.transaccion { con ->
            con.prepareStatement(
                "SELECT codigo, nombre, icono FROM servicios " +
                    "ORDER BY CASE codigo WHEN 'LUZ' THEN 1 WHEN 'AGUA' THEN 2 WHEN 'INTERNET' THEN 3 ELSE 4 END, creado, codigo"
            ).use { ps ->
                ps.executeQuery().use { rs ->
                    buildList { while (rs.next()) add(ServicioRespuesta(rs.getString("codigo"), rs.getString("nombre"), rs.getString("icono"))) }
                }
            }
        }
    }

    // Agrega un servicio nuevo, ej. GAS (solo el laboratorio).
    suspend fun crearServicio(req: NuevoServicioRequest): ServicioRespuesta = withContext(Dispatchers.IO) {
        val codigo = req.codigo.trim().uppercase()
        val nombre = req.nombre.trim()
        val icono = req.icono.trim().uppercase()
        if (!PATRON_CODIGO_SERVICIO.matches(codigo)) throw datoInvalido("El código debe tener de 3 a 10 letras, sin espacios (ej. GAS)")
        if (nombre.length !in 3..40) throw datoInvalido("Escribe el nombre del servicio (3 a 40 caracteres)")
        if (icono !in ICONOS_SERVICIO) throw datoInvalido("Ícono no válido. Usa: $ICONOS_SERVICIO")
        try {
            BaseDeDatos.transaccion { con ->
                con.prepareStatement("INSERT INTO servicios (codigo, nombre, icono) VALUES (?, ?, ?)").use { ps ->
                    ps.setString(1, codigo); ps.setString(2, nombre); ps.setString(3, icono); ps.executeUpdate()
                }
            }
        } catch (e: SQLIntegrityConstraintViolationException) {
            throw servicioExiste(codigo)        // Ya existe ese código.
        }
        log.info("Servicio creado: $codigo ($nombre, ícono $icono)")
        ServicioRespuesta(codigo, nombre, icono)
    }

    // Busca las facturas de una persona por su cédula (primero las que faltan pagar).
    suspend fun porCedula(cedula: String, servicio: String): List<ContratoRespuesta> = withContext(Dispatchers.IO) {
        val ced = cedula.trim()
        val serv = servicio.trim().uppercase()
        if (!cedulaValida(ced)) throw cedulaInvalida()
        BaseDeDatos.transaccion { con ->
            validarServicio(con, serv)
            con.prepareStatement(
                "SELECT $COLUMNAS_FACTURA FROM contratos WHERE cedula = ? AND servicio = ? ORDER BY pagado, codigo DESC"
            ).use { ps ->
                ps.setString(1, ced); ps.setString(2, serv)
                ps.executeQuery().use { rs -> buildList { while (rs.next()) add(leerFactura(rs)) } }
            }
        }
    }

    // Lista todas las facturas, con filtro opcional por estado y servicio (solo el laboratorio).
    suspend fun listar(estado: String?, servicio: String?): List<ContratoRespuesta> = withContext(Dispatchers.IO) {
        val condiciones = mutableListOf<String>()
        val valores = mutableListOf<String>()
        when (estado?.uppercase()) {
            "PENDIENTES" -> condiciones += "pagado = FALSE"
            "PAGADAS" -> condiciones += "pagado = TRUE"
        }
        servicio?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }?.let { condiciones += "servicio = ?"; valores += it }
        val where = if (condiciones.isEmpty()) "" else "WHERE " + condiciones.joinToString(" AND ")
        BaseDeDatos.transaccion { con ->
            // Primero las pendientes; luego las pagadas, las más recientes arriba.
            con.prepareStatement("SELECT $COLUMNAS_FACTURA FROM contratos $where ORDER BY pagado, fecha_pago DESC, codigo DESC").use { ps ->
                valores.forEachIndexed { i, v -> ps.setString(i + 1, v) }
                ps.executeQuery().use { rs -> buildList { while (rs.next()) add(leerFactura(rs)) } }
            }
        }
    }

    // Emite una factura nueva sin pagar para una cédula (solo el laboratorio).
    suspend fun crear(req: NuevaFacturaRequest): ContratoRespuesta = withContext(Dispatchers.IO) {
        val cedula = req.cedula.trim()
        val titular = req.titular.trim()
        val servicio = req.servicio.trim().uppercase()
        val periodo = req.periodo.trim()
        if (!cedulaValida(cedula)) throw cedulaInvalida()
        if (titular.length !in 3..100) throw datoInvalido("Escribe el nombre del titular (3 a 100 caracteres)")
        if (periodo.length !in 3..20) throw datoInvalido("Escribe el periodo (ej. Diciembre 2026)")
        val monto = validarMonto(req.monto).setScale(2, RoundingMode.HALF_UP)
        BaseDeDatos.transaccion { con -> validarServicio(con, servicio) }
        val prefijo = PREFIJOS[servicio] ?: servicio.take(2)
        // Código = letras del servicio + 6 números al azar; si ya existe, prueba otro (hasta 5 veces).
        repeat(5) {
            val codigo = prefijo + Random.nextInt(100_000, 1_000_000)
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
                log.info("Factura emitida: $servicio $codigo de $titular ($cedula), $periodo, $monto")
                return@withContext ContratoRespuesta(codigo, servicio, titular, monto.toDouble(), false, cedula, periodo)
            } catch (e: SQLIntegrityConstraintViolationException) {
                log.info("Código $codigo repetido, se intenta con otro")
            }
        }
        throw datoInvalido("No se pudo generar un código de contrato; intenta de nuevo")
    }

    // Revisa que el servicio exista; si no, error 400.
    private fun validarServicio(con: Connection, servicio: String) {
        val validos = codigosServicio(con)
        if (servicio !in validos) throw datoInvalido("Servicio no válido. Usa: $validos")
    }
}
