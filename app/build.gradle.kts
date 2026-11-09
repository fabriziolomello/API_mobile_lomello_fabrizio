import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // kotlinx.serialization: genera los serializadores de los DTO en compilación
    alias(libs.plugins.kotlin.serialization)
    // KSP: procesa las anotaciones de Room en compilación (reemplaza a kapt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
    // Firebase: lee app/google-services.json (no se sube a git)
    alias(libs.plugins.google.services)
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

// Firma del release: keystore.properties en la raíz del proyecto (NO se sube a git, igual
// que el .jks). Claves esperadas: storeFile, storePassword, keyAlias, keyPassword.
// storeFile puede ser absoluto o relativo a la raíz del proyecto.
// Nunca se imprimen valores: los errores solo nombran las claves que faltan.
val propiedadesKeystore = Properties().apply {
    val archivo = providers.fileContents(rootProject.layout.projectDirectory.file("keystore.properties"))
    archivo.asText.orNull?.let { load(it.reader()) }
}
val archivoKeystoreRelease: File? = propiedadesKeystore.getProperty("storeFile")
    ?.takeIf { it.isNotBlank() }
    ?.let { rootProject.file(it.trim()) }
// null = se puede firmar; si no, el motivo (sin datos sensibles)
val problemaFirmaRelease: String? = run {
    val claves = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
    val faltantes = claves.filter { propiedadesKeystore.getProperty(it).isNullOrBlank() }
    when {
        propiedadesKeystore.isEmpty -> "no se encontró keystore.properties en la raíz del proyecto"
        faltantes.isNotEmpty() -> "a keystore.properties le faltan las claves: ${faltantes.joinToString()}"
        archivoKeystoreRelease?.isFile != true -> "no existe el archivo indicado en storeFile de keystore.properties"
        else -> null
    }
}

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

    signingConfigs {
        // Solo se crea si keystore.properties está completo y el .jks existe.
        // Sin eso, debug y los tests siguen funcionando; el release falla (ver verificarFirmaRelease).
        if (problemaFirmaRelease == null) {
            create("release") {
                storeFile = archivoKeystoreRelease
                storePassword = propiedadesKeystore.getProperty("storePassword")
                keyAlias = propiedadesKeystore.getProperty("keyAlias")
                keyPassword = propiedadesKeystore.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // AGP 9: optimization.enable activa R8 (código: shrink + optimización + ofuscación)
            // y la reducción de recursos; reemplaza a isMinifyEnabled / isShrinkResources.
            // packageScope queda en su valor por defecto ("**"): se optimiza todo, incluido el
            // código de la app. Las reglas keep propias están en src/main/keepRules/rules.keep.
            optimization {
                enable = true
            }
            signingConfig = signingConfigs.findByName("release")
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

// Sin firma válida, el release NO se genera (ni sin firmar): falla con un mensaje claro.
// Solo depende de esta tarea el empaquetado/firma del release; debug y tests no la ven.
val verificarFirmaRelease = tasks.register("verificarFirmaRelease") {
    group = "verification"
    description = "Verifica que keystore.properties permita firmar el release"
    val problema = problemaFirmaRelease // solo un String: compatible con configuration cache
    doLast {
        if (problema != null) {
            throw GradleException(
                "No se puede firmar el release: $problema. " +
                    "Creá keystore.properties en la raíz (storeFile, storePassword, keyAlias, keyPassword)."
            )
        }
    }
}
tasks.matching { it.name == "packageRelease" || it.name == "signReleaseBundle" }.configureEach {
    dependsOn(verificarFirmaRelease)
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
    // Actividad 4: Firebase (versiones definidas por el BoM)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    // await() sobre los Task de Firebase (reemplaza a los módulos -ktx eliminados)
    implementation(libs.kotlinx.coroutines.play.services)
    testImplementation(libs.junit)
    // Dispatchers.setMain para testear ViewModels (viewModelScope usa Main)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}