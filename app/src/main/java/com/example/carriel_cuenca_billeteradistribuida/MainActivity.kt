package com.example.carriel_cuenca_billeteradistribuida

import android.os.Bundle                                   // Datos que Android pasa al crear la pantalla.
import android.widget.Toast                                // Mensajito flotante temporal.
import androidx.activity.ComponentActivity                 // Clase base de una pantalla que usa Compose.
import androidx.activity.compose.setContent                // Define qué dibuja la Activity con Compose.
import androidx.activity.enableEdgeToEdge                  // Dibuja de borde a borde (detrás de las barras).
import androidx.compose.foundation.layout.fillMaxSize       // Ocupar toda la pantalla.
import androidx.compose.foundation.layout.padding          // Margen interno.
import androidx.compose.material3.Scaffold                 // Estructura base de una pantalla Material.
import androidx.compose.runtime.getValue                   // Permite usar "by" para leer un State.
import androidx.compose.ui.Modifier                        // Modificadores de Compose.
import androidx.lifecycle.compose.collectAsStateWithLifecycle // Convierte un Flow en estado de Compose.
import androidx.lifecycle.lifecycleScope                   // "Ámbito" de corrutinas atado a esta pantalla.
import com.example.carriel_cuenca_billeteradistribuida.data.local.BilleteraDatabase
import com.example.carriel_cuenca_billeteradistribuida.data.local.MovimientoDao
import com.example.carriel_cuenca_billeteradistribuida.data.local.aEntidad
import com.example.carriel_cuenca_billeteradistribuida.data.local.aModelo
import com.example.carriel_cuenca_billeteradistribuida.model.CATEGORIA_SERVICIO_BASICO
import com.example.carriel_cuenca_billeteradistribuida.model.Movimiento
import com.example.carriel_cuenca_billeteradistribuida.model.TipoOperacion
import com.example.carriel_cuenca_billeteradistribuida.ui.DashboardScreen
import com.example.carriel_cuenca_billeteradistribuida.ui.DatosDePrueba
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.Carriel_Cuenca_BilleteraDistribuidaTheme
import com.example.carriel_cuenca_billeteradistribuida.util.logHilo
import kotlinx.coroutines.Dispatchers                      // Dispatchers.IO (fondo) y Dispatchers.Main (UI).
import kotlinx.coroutines.flow.flowOn                      // Cambia el hilo donde se PRODUCE un Flow.
import kotlinx.coroutines.flow.map                         // Transforma cada valor que emite un Flow.
import kotlinx.coroutines.flow.onEach                      // Ejecuta algo con cada valor que pasa por el Flow.
import kotlinx.coroutines.launch                           // Lanza una corrutina nueva.
import kotlinx.coroutines.withContext                      // Ejecuta un bloque en otro hilo y vuelve.

// La Activity es la "puerta de entrada" de la app: Android la abre al tocar el ícono.
// ":" significa "hereda de" ComponentActivity.
// NOTA: en la Fase 3 esta lógica de datos se moverá al ViewModel y al Repository.
class MainActivity : ComponentActivity() {

    // onCreate se ejecuta una vez cuando la pantalla se crea.
    override fun onCreate(savedInstanceState: Bundle?) {   // "Bundle?" puede ser null (null safety).
        super.onCreate(savedInstanceState)                 // Primero dejamos que Android haga lo suyo.
        enableEdgeToEdge()                                 // Contenido de borde a borde.

        // Obtenemos el DAO de la base de datos (la base se crea solo la primera vez).
        val dao = BilleteraDatabase.obtener(applicationContext).movimientoDao()

        // FLUJO DEL HISTORIAL (lectura reactiva desde Room):
        val flujoHistorial = dao.observarTodos()           // 1. Room emite la lista cada vez que la tabla cambia.
            .map { entidades ->                            // 2. Convertimos cada fila (Entity) en modelo de la app.
                logHilo("Room: leyendo historial (${entidades.size} filas)") // Debe decir DefaultDispatcher-worker.
                entidades.map { it.aModelo() }
            }
            // 3. flowOn(Dispatchers.IO): todo lo de ARRIBA (consulta + map) corre en el hilo IO.
            .flowOn(Dispatchers.IO)
            // 4. Lo de ABAJO corre en el hilo de quien recolecta: la UI (main).
            .onEach { lista -> logHilo("UI: recibió ${lista.size} movimientos") } // Debe decir main.

        // setContent { } = a partir de aquí, la interfaz se dibuja con Compose.
        setContent {
            // Recolectamos el Flow y lo convertimos en ESTADO de Compose.
            // "by" = delegación: "movimientos" se lee como una lista normal.
            // Cada vez que Room emite una lista nueva, la pantalla se redibuja sola.
            // "WithLifecycle": deja de escuchar cuando la app pasa a segundo plano (ahorra batería).
            val movimientos by flujoHistorial.collectAsStateWithLifecycle(initialValue = emptyList())

            // Aplicamos el tema (colores y tipografía) a toda la app.
            Carriel_Cuenca_BilleteraDistribuidaTheme {
                // Scaffold calcula el espacio de las barras del sistema (innerPadding).
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    DashboardScreen(
                        saldo = DatosDePrueba.saldo,       // Saldo falso hasta la Fase 4 (servidor).
                        enLinea = false,                   // Aún no hay WebSocket: "Desconectado".
                        movimientos = movimientos,         // ¡Ahora viene de Room!
                        onAccionClick = { tipo ->
                            // lifecycleScope.launch: inicia una corrutina en el hilo MAIN,
                            // que se cancela sola si la pantalla se destruye.
                            lifecycleScope.launch {
                                guardarMovimientoDePrueba(dao, tipo)
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)         // Respetamos el espacio de las barras.
                    )
                }
            }
        }
    }

    // PRUEBA DE LA FASE 2: guarda un movimiento de $10 en Room al tocar un botón.
    // En la Fase 4 esto se reemplaza por la llamada real al servidor.
    // "suspend": puede pausarse (mientras espera a Room) sin congelar la pantalla.
    private suspend fun guardarMovimientoDePrueba(dao: MovimientoDao, tipo: TipoOperacion) {
        logHilo("Click en ${tipo.etiqueta}")               // Corre en main (el click viene de la UI).

        // Creamos el comprobante. El id queda en 0 para que Room lo genere.
        val movimiento = Movimiento(
            tipo = tipo,
            monto = 10.0,
            fechaHora = System.currentTimeMillis(),        // Fecha y hora de ahora.
            detalle = "Prueba de Room (Fase 2)",
            // "if" como expresión: solo el pago de servicio lleva categoría; los demás, null.
            categoria = if (tipo == TipoOperacion.PAGO_SERVICIO) CATEGORIA_SERVICIO_BASICO else null
        )

        // withContext(Dispatchers.IO): SALTAMOS al hilo de fondo para escribir en la base de datos.
        // La corrutina se "suspende" aquí; el hilo main queda libre para dibujar la UI.
        val idNuevo = withContext(Dispatchers.IO) {
            logHilo("Room: insertando ${tipo.etiqueta}")   // Debe decir DefaultDispatcher-worker.
            dao.insertar(movimiento.aEntidad())            // Devuelve el id generado (último valor del bloque).
        }

        // Al terminar withContext, VOLVEMOS automáticamente al hilo main.
        logHilo("Guardado con id=$idNuevo, de vuelta en la UI") // Debe decir main.
        Toast.makeText(this, "Guardado en Room: ${tipo.etiqueta}", Toast.LENGTH_SHORT).show()
        // No actualizamos la lista a mano: Room avisa al Flow y la pantalla se redibuja sola.
    }
}
