// =====================================================================
//   Es la tabla del teléfono donde se guarda la sesión abierta, para no
//   pedir la contraseña cada vez que se abre la app.
// =====================================================================

package com.example.carriel_cuenca_billeteradistribuida.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

// Datos de la sesión abierta (solo hay una a la vez).
@Entity(tableName = "sesion")
data class SesionEntity(
    @PrimaryKey val id: Int = 0,        // Siempre 0: solo hay una sesión a la vez.
    val token: String,                  // "Pase" que se manda al servidor en cada petición.
    val usuario: String,                // Ej. "pamela".
    val nombre: String,                 // Ej. "Pamela Carriel".
    val cuenta: String,                 // Ej. "1001".
    val minutosInactividad: Int,        // Minutos sin usar la app antes de cerrar la sesión.
    val ultimaActividad: Long           // Cuándo tocó el usuario la app por última vez.
)
