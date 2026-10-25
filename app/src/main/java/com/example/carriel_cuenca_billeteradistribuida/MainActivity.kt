// =====================================================================
//   Es la PUERTA DE ENTRADA de la app: lo primero que Android abre cuando
//   el usuario toca el ícono. Solo pone el tema y muestra AppRaiz.
// =====================================================================

package com.example.carriel_cuenca_billeteradistribuida

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.carriel_cuenca_billeteradistribuida.ui.AppRaiz
import com.example.carriel_cuenca_billeteradistribuida.ui.theme.Carriel_Cuenca_BilleteraDistribuidaTheme
import com.example.carriel_cuenca_billeteradistribuida.util.logHilo

// Pantalla de entrada: lo que el usuario ve lo dibuja AppRaiz adentro de ella.
class MainActivity : ComponentActivity() {

    // Se ejecuta al abrir la app (y al girar el teléfono).
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()                                 // La app ocupa toda la pantalla.
        logHilo("MainActivity: onCreate")                  // Mensaje de control en Logcat.
        // Lo que va aquí adentro es lo que se ve en pantalla.
        setContent {
            Carriel_Cuenca_BilleteraDistribuidaTheme {
                AppRaiz()
            }
        }
    }
}
