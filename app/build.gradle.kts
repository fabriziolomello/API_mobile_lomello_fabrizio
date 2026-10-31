import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // kotlinx.serialization: genera los serializadores de los DTO en compilación
    alias(libs.plugins.kotlin.serialization)
    // KSP: procesa las anotaciones de Room en compilación (reemplaza a kapt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

// URL base del servicio remoto, leída de local.properties (que NO se sube a git).
// Ver local.properties.example. Si falta, se usa una URL inválida: la app funciona
// igual en modo offline y la sincronización informa "sin conexión".
val propiedadesLocales = Properties().apply {
    val archivo = providers.fileContents(rootProject.layout.projectDirectory.file("local.properties"))
    archivo.asText.orNull?.let { load(it.reader()) }
}
val apiBaseUrl: String = (propiedadesLocales.getProperty("API_BASE_URL") ?: "https://sin-configurar.invalid/")
    .trim()
    .let { if (it.endsWith("/")) it else "$it/" } // Retrofit exige la barra final

android {
    namespace = "com.fabrizio.recordatorios"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.fabrizio.recordatorios"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        // BuildConfig.DEBUG (herramientas solo de debug) y BuildConfig.API_BASE_URL
        buildConfig = true
    }
}

// exportSchema = true: Room escribe el esquema de cada versión de la base en JSON.
// Se versiona en git y sirve para revisar cambios y testear migraciones.
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // Actividad 2: collectAsStateWithLifecycle, viewModel() y Navigation Compose
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    // Íconos básicos (Add, ArrowBack, MoreVert...) para TopAppBar y FAB
    implementation(libs.androidx.compose.material.icons.core)
    // Actividad 3: preferencias de usuario persistentes
    implementation(libs.androidx.datastore.preferences)
    // Actividad 3: base de datos local (fuente de verdad de los recordatorios)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    // Actividad 3: servicio remoto
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}