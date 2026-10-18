package com.example.carriel_cuenca_billeteradistribuida

import android.os.Bundle                                   // Datos que Android pasa al crear la pantalla.
import androidx.activity.ComponentActivity                 // Clase base de una pantalla que usa Compose.
import androidx.activity.compose.setContent                // Define qué dibuja la Activity con Compose.
import androidx.activity.enableEdgeToEdge                  // Dibuja de borde a borde (detrás de las barras).
import com.example.carriel_cuenca_billeteradistribuida.data.remote.ConfigServidor
import com.example.carriel_cuenca_billeteradistribuida.ui.AppRaiz
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.Carriel_Cuenca_BilleteraDistribuidaTheme
import com.example.carriel_cuenca_billeteradistribuida.util.logHilo

// La Activity es la "puerta de entrada" de la app: Android la abre al tocar el ícono.
// No contiene lógica: solo muestra AppRaiz, que decide entre Login/Registro y la billetera.
// La lógica vive en los ViewModels y el acceso a datos en los Repositories.
class MainActivity : ComponentActivity() {

    // onCreate se ejecuta cuando la pantalla se crea (también al ROTAR el teléfono).
    override fun onCreate(savedInstanceState: Bundle?) {   // "Bundle?" puede ser null (null safety).
        super.onCreate(savedInstanceState)                 // Primero dejamos que Android haga lo suyo.
        enableEdgeToEdge()                                 // Contenido de borde a borde.
        ConfigServidor.cargar(applicationContext)          // Recupera la dirección del servidor guardada.
        logHilo("MainActivity: onCreate")                  // Siempre en "main".
        setContent {
            // Aplicamos el tema y mostramos la app.
            Carriel_Cuenca_BilleteraDistribuidaTheme {
                AppRaiz()
            }
        }
    }
}
