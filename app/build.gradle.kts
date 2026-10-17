// Archivo de compilación del módulo "app" (nuestra aplicación).
plugins {
    // Convierte este módulo en una app Android. En AGP 9 ya incluye Kotlin,
    // por eso NO se agrega el plugin "kotlin-android".
    alias(libs.plugins.android.application)
    // Activa el compilador de Jetpack Compose.
    alias(libs.plugins.kotlin.compose)
    // Activa KSP para que Room genere el código de la base de datos.
    alias(libs.plugins.ksp)
}

android {
    // Paquete base del código Kotlin (se usa para generar la clase R).
    namespace = "com.example.carriel_cuenca_billeteradistribuida"
    // Versión del SDK de Android con la que se compila.
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        // Identificador único de la app en el dispositivo.
        applicationId = "com.example.carriel_cuenca_billeteradistribuida"
        // Android mínimo soportado: 8.0 (API 26).
        minSdk = 26
        // Android para el que está optimizada la app.
        targetSdk = 37
        // Número interno de versión (sube en cada entrega).
        versionCode = 1
        // Versión visible para el usuario.
        versionName = "1.0"

        // Ejecutor de pruebas instrumentadas (plantilla).
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // Optimiza y reduce el APK de release.
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        // Versión de Java del bytecode generado.
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        // Habilita Jetpack Compose en este módulo.
        compose = true
    }
}

dependencies {
    // --- Jetpack Compose (el BOM elige versiones compatibles) ---
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    // Íconos de Material (billetera, rayo, gota, wifi...).
    implementation(libs.androidx.compose.material.icons.extended)

    // --- AndroidX básicos ---
    implementation(libs.androidx.core.ktx)
    // --- Lifecycle: ViewModel y lectura de StateFlow en Compose ---
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // --- Room: base de datos local ---
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    // "ksp" (no "implementation"): el compilador de Room solo se usa al compilar.
    ksp(libs.androidx.room.compiler)

    // --- Red: Retrofit (REST) y OkHttp (HTTP + WebSocket) ---
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    // --- Corrutinas (Dispatchers.IO / Dispatchers.Main) ---
    implementation(libs.kotlinx.coroutines.android)

    // --- Pruebas (plantilla) ---
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
