package com.example.carriel_cuenca_billeteradistribuida

import android.os.Bundle                                   // Datos que Android pasa al crear la pantalla.
import android.widget.Toast                                // Mensajito flotante temporal.
import androidx.activity.ComponentActivity                 // Clase base de una pantalla que usa Compose.
import androidx.activity.compose.setContent                // Define qué dibuja la Activity con Compose.
import androidx.activity.enableEdgeToEdge                  // Dibuja de borde a borde (detrás de las barras).
import androidx.compose.foundation.layout.fillMaxSize       // Ocupar toda la pantalla.
import androidx.compose.foundation.layout.padding          // Margen interno.
import androidx.compose.material3.Scaffold                 // Estructura base de una pantalla Material.
import androidx.compose.ui.Modifier                        // Modificadores de Compose.
import com.example.carriel_cuenca_billeteradistribuida.ui.DashboardScreen
import com.example.carriel_cuenca_billeteradistribuida.ui.DatosDePrueba
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.Carriel_Cuenca_BilleteraDistribuidaTheme

// La Activity es la "puerta de entrada" de la app: Android la abre al tocar el ícono.
// ":" significa "hereda de" ComponentActivity.
class MainActivity : ComponentActivity() {

    // onCreate se ejecuta una vez cuando la pantalla se crea.
    override fun onCreate(savedInstanceState: Bundle?) {   // "Bundle?" puede ser null (null safety).
        super.onCreate(savedInstanceState)                 // Primero dejamos que Android haga lo suyo.
        enableEdgeToEdge()                                 // Contenido de borde a borde.
        // setContent { } = a partir de aquí, la interfaz se dibuja con Compose.
        setContent {
            // Aplicamos el tema (colores y tipografía) a toda la app.
            Carriel_Cuenca_BilleteraDistribuidaTheme {
                // Scaffold calcula el espacio de las barras del sistema (innerPadding)
                // para que nuestro contenido no quede debajo de ellas.
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // Mostramos el dashboard con DATOS FALSOS (Fase 1).
                    DashboardScreen(
                        saldo = DatosDePrueba.saldo,
                        enLinea = false,                   // Aún no hay WebSocket: "Desconectado".
                        movimientos = DatosDePrueba.movimientos,
                        // Lambda: por ahora, al tocar un botón solo mostramos un Toast.
                        // En la Fase 6 aquí se abrirá el diálogo correspondiente.
                        onAccionClick = { tipo ->
                            Toast.makeText(
                                this@MainActivity,         // Contexto: esta Activity.
                                "Tocaste: ${tipo.etiqueta}", // "${...}" inserta un valor en el texto.
                                Toast.LENGTH_SHORT         // Duración corta.
                            ).show()
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)         // Respetamos el espacio de las barras.
                    )
                }
            }
        }
    }
}
