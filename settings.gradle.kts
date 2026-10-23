// CONFIGURACIÓN DE GRADLE (settings): es lo PRIMERO que lee Gradle, antes de compilar.
// Dice qué MÓDULOS forman el proyecto y de qué REPOSITORIOS se descargan plugins y librerías.

// pluginManagement: dónde buscar los PLUGINS de Gradle (ej. el plugin de Android).
pluginManagement {
    repositories {
        google {                                                // Repositorio de Google (plugins y librerías de Android).
            // content: en el repositorio de Google solo se buscan estos grupos (así las búsquedas son más rápidas).
            // "com\\.android.*" = grupos que empiezan con "com.android" ("\\." es un punto literal en la regex).
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()                                          // Repositorio central de Maven (la mayoría de librerías Java/Kotlin).
        gradlePluginPortal()                                    // Portal oficial de plugins de Gradle.
    }
}
// Plugin foojay: si falta la versión de Java (JDK) que pide el proyecto, Gradle la descarga sola.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
// dependencyResolutionManagement: dónde buscar las LIBRERÍAS (dependencias) de todos los módulos.
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)  // Prohíbe que un módulo declare sus propios repositorios.
    repositories {
        google()
        mavenCentral()
    }
}

// Nombre del proyecto y lista de módulos. ":app" es la carpeta app/ (nuestra aplicación).
rootProject.name = "Carriel_Cuenca_BilleteraDistribuida"
include(":app")
