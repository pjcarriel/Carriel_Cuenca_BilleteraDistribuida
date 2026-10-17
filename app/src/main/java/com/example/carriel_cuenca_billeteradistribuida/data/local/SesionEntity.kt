// Paquete de la base de datos local.
package com.example.carriel_cuenca_billeteradistribuida.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

// Tabla "sesion": la sesión ABIERTA en este teléfono (como máximo una fila, con id = 0).
// Permite abrir la app sin volver a escribir la contraseña mientras la sesión siga vigente.
// Se guarda en el almacenamiento PRIVADO de la app (otras apps no pueden leerlo).
@Entity(tableName = "sesion")
data class SesionEntity(
    @PrimaryKey val id: Int = 0,        // Siempre 0: solo hay una sesión a la vez.
    val token: String,                  // Credencial que se envía al servidor en cada petición.
    val usuario: String,                // Ej. "pamela".
    val nombre: String,                 // Ej. "Pamela Carriel".
    val cuenta: String,                 // Ej. "1001".
    val minutosInactividad: Int,        // Minutos sin actividad tras los que se cierra la sesión.
    val ultimaActividad: Long           // Último toque del usuario (milisegundos).
)
