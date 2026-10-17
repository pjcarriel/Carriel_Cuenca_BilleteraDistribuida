// Archivo de compilación de NIVEL SUPERIOR (todo el proyecto).
// Aquí solo se DECLARAN los plugins; "apply false" significa
// "no lo apliques aquí, cada módulo (como :app) decide si lo usa".
plugins {
    // Plugin de Android para crear una aplicación (APK).
    alias(libs.plugins.android.application) apply false
    // Plugin del compilador de Jetpack Compose.
    alias(libs.plugins.kotlin.compose) apply false
    // Plugin KSP: genera código a partir de anotaciones (lo necesita Room).
    alias(libs.plugins.ksp) apply false
}
