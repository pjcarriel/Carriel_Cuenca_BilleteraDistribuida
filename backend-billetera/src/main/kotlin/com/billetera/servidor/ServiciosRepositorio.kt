// Paquete del servidor.
package com.billetera.servidor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.math.RoundingMode
import java.sql.Connection
import java.sql.SQLIntegrityConstraintViolationException     // MySQL: se repitió una llave primaria.
import kotlin.random.Random

private val log = LoggerFactory.getLogger("ServiciosRepositorio")

// Íconos que la app sabe dibujar para un servicio (la app los traduce a un ícono de Material).
val ICONOS_SERVICIO = setOf("RAYO", "GOTA", "WIFI", "TELEFONO", "GAS", "TV", "BASURA", "OTRO")

// Código de un servicio: de 3 a 10 letras MAYÚSCULAS sin espacios (ej. "GAS", "TELEFONO").
private val PATRON_CODIGO_SERVICIO = Regex("^[A-Z]{3,10}$")

// Prefijo del código de contrato de las facturas NUEVAS (ej. "LZ482913"). Para los servicios que
// cree el laboratorio se usan las 2 primeras letras de su código (ej. GAS -> "GA").
private val PREFIJOS = mapOf("LUZ" to "LZ", "AGUA" to "AG", "INTERNET" to "IN")

// SERVICIOS BÁSICOS Y SUS FACTURAS (Fase 8):
//  - Cualquier usuario puede ver el CATÁLOGO de servicios y BUSCAR las facturas de una cédula.
//  - Solo el LABORATORIO (la "empresa de servicios") puede crear SERVICIOS nuevos, listar TODAS las
//    facturas y EMITIR facturas nuevas.
// Igual que los demás repositorios: JDBC bloquea el hilo, así que todo corre en Dispatchers.IO.
object ServiciosRepositorio {

    // GET /servicios -> catálogo de servicios (LUZ, AGUA, INTERNET y los que se hayan agregado).
    suspend fun servicios(): List<ServicioRespuesta> = withContext(Dispatchers.IO) {
        BaseDeDatos.transaccion { con ->
            // Orden: primero los 3 de siempre (LUZ, AGUA, INTERNET) y luego los nuevos, del más antiguo al más nuevo.
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

    // POST /admin/servicios (solo laboratorio): agrega un servicio nuevo al catálogo (ej. GAS).
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
            throw servicioExiste(codigo)        // La llave primaria detectó un código repetido -> 409.
        }
        log.info("Servicio creado: $codigo ($nombre, ícono $icono)")
        ServicioRespuesta(codigo, nombre, icono)
    }

    // GET /facturas?cedula=...&servicio=...  -> facturas de esa cédula y ese servicio.
    // Primero las PENDIENTES (pagado = FALSE va antes que TRUE) y luego por código.
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

    // GET /admin/facturas?estado=PENDIENTES|PAGADAS|TODAS&servicio=LUZ  (solo laboratorio).
    // La consulta se ARMA según los filtros, pero los valores siempre van con "?" (sin inyección SQL).
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
            // Las pendientes primero; las pagadas después, las más recientes arriba.
            con.prepareStatement("SELECT $COLUMNAS_FACTURA FROM contratos $where ORDER BY pagado, fecha_pago DESC, codigo DESC").use { ps ->
                valores.forEachIndexed { i, v -> ps.setString(i + 1, v) }
                ps.executeQuery().use { rs -> buildList { while (rs.next()) add(leerFactura(rs)) } }
            }
        }
    }

    // POST /admin/facturas (solo laboratorio): EMITE una factura PENDIENTE con un código nuevo.
    // Es lo que hace la empresa de servicios cada mes: le "cobra" a una persona (por su cédula);
    // luego esa persona la ve al buscar su cédula en "Pagar servicios" y la paga.
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
        // El código es el prefijo del servicio + 6 dígitos al azar. Si por casualidad ya existe
        // (la llave primaria lo rechaza), se intenta con otro número (hasta 5 veces).
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

    // Lanza 400 si el servicio no existe en el catálogo (tabla servicios).
    private fun validarServicio(con: Connection, servicio: String) {
        val validos = codigosServicio(con)
        if (servicio !in validos) throw datoInvalido("Servicio no válido. Usa: $validos")
    }
}
