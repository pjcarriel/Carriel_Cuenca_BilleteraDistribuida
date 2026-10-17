// Archivo de compilación del SERVIDOR.
plugins {
    // Compila código Kotlin para la JVM (Java Virtual Machine), no para Android.
    kotlin("jvm") version "2.2.10"
    // Genera el código que convierte nuestras data class <-> JSON (@Serializable).
    kotlin("plugin.serialization") version "2.2.10"
    // Permite ejecutar el servidor con "gradlew run".
    application
}

// Versiones de las librerías del servidor (en un solo lugar).
val ktorVersion = "3.1.3"

dependencies {
    // --- Ktor: framework de servidor web hecho en Kotlin ---
    implementation("io.ktor:ktor-server-core:$ktorVersion")                  // Núcleo del servidor.
    implementation("io.ktor:ktor-server-cio:$ktorVersion")                   // Motor de red CIO (Coroutine I/O): atiende conexiones con corrutinas.
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")   // Recibir/enviar JSON automáticamente.
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")  // Conversor JSON (kotlinx.serialization).
    implementation("io.ktor:ktor-server-websockets:$ktorVersion")            // Soporte de WebSocket.
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")          // Convertir excepciones en respuestas HTTP (400, 404, 409).

    // --- Base de datos MySQL ---
    implementation("com.mysql:mysql-connector-j:8.4.0")                      // Driver JDBC: permite a Kotlin hablar con MySQL.
    implementation("com.zaxxer:HikariCP:5.1.0")                              // Pool de conexiones: reutiliza conexiones (más rápido).

    // --- Logs (mensajes en consola) ---
    implementation("ch.qos.logback:logback-classic:1.5.6")                   // Imprime los logs con hora e hilo.
}

application {
    // Clase que contiene la función main() (archivo Application.kt -> clase ApplicationKt).
    mainClass.set("com.billetera.servidor.ApplicationKt")
}
