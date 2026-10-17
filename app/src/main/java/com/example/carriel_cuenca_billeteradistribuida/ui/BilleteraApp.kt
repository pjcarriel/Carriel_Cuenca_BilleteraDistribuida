// Paquete de la interfaz gráfica.
package com.example.carriel_cuenca_billeteradistribuida.ui

import androidx.compose.foundation.layout.fillMaxSize                 // Ocupar toda la pantalla.
import androidx.compose.foundation.layout.padding                     // Margen interno.
import androidx.compose.material3.Scaffold                            // Estructura base de la pantalla.
import androidx.compose.material3.SnackbarHost                        // Lugar donde aparecen los Snackbars.
import androidx.compose.material3.SnackbarHostState                   // Controla qué Snackbar se muestra.
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect                        // Lanza una corrutina desde Compose.
import androidx.compose.runtime.getValue                              // Permite usar "by".
import androidx.compose.runtime.remember                              // Recuerda un valor entre redibujados.
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle         // StateFlow -> estado de Compose.
import androidx.lifecycle.viewmodel.compose.viewModel                 // Obtiene (o crea) el ViewModel.
import com.example.carriel_cuenca_billeteradistribuida.model.EstadoOperacion

// Pantalla "raíz" de la app: une el ViewModel con la UI.
// "= viewModel(...)" es un valor por defecto: Android crea el ViewModel con nuestra Factory
// la primera vez, y en una rotación DEVUELVE EL MISMO (por eso no se pierde el saldo).
@Composable
fun BilleteraApp(viewModel: BilleteraViewModel = viewModel(factory = BilleteraViewModel.Factory)) {

    // Leemos cada StateFlow del ViewModel como estado de Compose.
    // Cuando cualquiera cambia, Compose redibuja SOLO lo que depende de él.
    val saldo by viewModel.saldo.collectAsStateWithLifecycle()
    val enLinea by viewModel.enLinea.collectAsStateWithLifecycle()
    val historial by viewModel.historial.collectAsStateWithLifecycle()
    val estado by viewModel.estado.collectAsStateWithLifecycle()

    // Estado del Snackbar (mensaje temporal en la parte inferior).
    // "remember" evita que se cree uno nuevo en cada redibujado.
    val snackbarHostState = remember { SnackbarHostState() }

    // LaunchedEffect(estado): se ejecuta cada vez que "estado" cambia.
    // Es una corrutina en el hilo main (showSnackbar es una función suspend).
    LaunchedEffect(estado) {
        // "when" con "val": guardamos el valor actual para que Kotlin sepa su tipo exacto
        // dentro de cada rama (smart cast) y podamos leer ".mensaje".
        when (val e = estado) {
            is EstadoOperacion.Exito -> {
                snackbarHostState.showSnackbar("✔ ${e.mensaje}")   // Espera a que el mensaje se oculte...
                viewModel.mensajeMostrado()                          // ...y avisa al ViewModel.
            }
            is EstadoOperacion.Error -> {
                snackbarHostState.showSnackbar("✖ ${e.mensaje}")
                viewModel.mensajeMostrado()
            }
            // Inactivo y Procesando: no mostramos nada. "Unit" = "no hacer nada".
            EstadoOperacion.Inactivo, EstadoOperacion.Procesando -> Unit
        }
    }

    // Scaffold con lugar para Snackbars.
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        DashboardScreen(
            saldo = saldo,
            enLinea = enLinea,
            movimientos = historial,
            // "viewModel::ejecutarOperacion" = REFERENCIA a una función: es lo mismo que
            // escribir { tipo -> viewModel.ejecutarOperacion(tipo) }, pero más corto.
            onAccionClick = viewModel::ejecutarOperacion,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }
}
