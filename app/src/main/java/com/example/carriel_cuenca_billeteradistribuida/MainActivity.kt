package com.example.carriel_cuenca_billeteradistribuida

import android.os.Bundle                                   // Datos que Android pasa al crear la pantalla.
import androidx.activity.ComponentActivity                 // Clase base de una pantalla que usa Compose.
import androidx.activity.compose.setContent                // Define qué dibuja la Activity con Compose.
import androidx.activity.enableEdgeToEdge                  // Dibuja de borde a borde (detrás de las barras).
import com.example.carriel_cuenca_billeteradistribuida.ui.BilleteraApp
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.Carriel_Cuenca_BilleteraDistribuidaTheme
import com.example.carriel_cuenca_billeteradistribuida.util.logHilo

// La Activity es la "puerta de entrada" de la app: Android la abre al tocar el ícono.
// Desde la Fase 3 (MVVM) ya no contiene lógica: solo muestra BilleteraApp.
// La lógica vive en el ViewModel y el acceso a datos en el Repository.
class MainActivity : ComponentActivity() {

    // onCreate se ejecuta cuando la pantalla se crea (también al ROTAR el teléfono).
    override fun onCreate(savedInstanceState: Bundle?) {   // "Bundle?" puede ser null (null safety).
        super.onCreate(savedInstanceState)                 // Primero dejamos que Android haga lo suyo.
        enableEdgeToEdge()                                 // Contenido de borde a borde.
        logHilo("MainActivity: onCreate")                  // Siempre en "main".
        setContent {
            // Aplicamos el tema y mostramos la app.
            Carriel_Cuenca_BilleteraDistribuidaTheme {
                BilleteraApp()
            }
        }
    }
}
