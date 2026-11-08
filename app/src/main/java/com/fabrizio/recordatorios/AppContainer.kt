package com.fabrizio.recordatorios

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.withTransaction
import com.fabrizio.recordatorios.data.LocalFirstReminderRepository
import com.fabrizio.recordatorios.data.ReminderRepository
import com.fabrizio.recordatorios.data.auth.AuthRepository
import com.fabrizio.recordatorios.data.auth.FirebaseAuthRepository
import com.fabrizio.recordatorios.data.local.AppDatabase
import com.fabrizio.recordatorios.data.preferencias.PreferenciasRepository
import com.fabrizio.recordatorios.data.preferencias.PreferenciasRepositoryDataStore
import com.fabrizio.recordatorios.data.remote.NetworkModule
import com.fabrizio.recordatorios.data.remote.RetrofitReminderRemoteDataSource
import com.fabrizio.recordatorios.data.remote.SimuladorFallas
import com.fabrizio.recordatorios.data.sync.AndroidConnectivityObserver
import com.fabrizio.recordatorios.data.sync.ConnectivityObserver
import com.fabrizio.recordatorios.data.sync.SyncManager
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

// Delegate a nivel de archivo: garantiza UNA sola instancia de DataStore por proceso.
// Tener dos instancias sobre el mismo archivo hace que DataStore lance una excepción.
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "preferencias_usuario")

// Inyección de dependencias manual: un único lugar donde se crean las dependencias
// de la app. Las factories de los ViewModel las toman de acá.
class AppContainer(context: Context) {

    // Se guarda el applicationContext para no retener una Activity
    private val appContext = context.applicationContext

    // Scope que vive mientras viva el proceso (para la sincronización en segundo plano).
    // SupervisorJob: si un sync falla, el scope sigue sirviendo para los siguientes.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Base de datos singleton (ver AppDatabase.getInstance)
    private val database: AppDatabase by lazy { AppDatabase.getInstance(appContext) }

    val preferencias: PreferenciasRepository by lazy {
        PreferenciasRepositoryDataStore(appContext.dataStore)
    }

    val conectividad: ConnectivityObserver by lazy { AndroidConnectivityObserver(appContext) }

    // Solo tiene efecto en debug (el interceptor no se agrega en release)
    val simuladorFallas: SimuladorFallas by lazy { SimuladorFallas() }

    // URL base desde BuildConfig (definida en local.properties, nunca en el código)
    private val api by lazy { NetworkModule.crearApi(BuildConfig.API_BASE_URL, simuladorFallas) }

    val syncManager: SyncManager by lazy {
        SyncManager(
            dao = database.reminderDao(),
            remoto = RetrofitReminderRemoteDataSource(api),
            conectividad = conectividad,
            appScope = appScope,
            enTransaccion = { bloque -> database.withTransaction { bloque() } },
            registrarSincronizacion = preferencias::guardarUltimaSincronizacion
        )
    }

    // Se expone como la interfaz: el resto de la app no conoce la implementación
    val repositorio: ReminderRepository by lazy {
        LocalFirstReminderRepository(database.reminderDao(), syncManager, preferencias)
    }

    // Actividad 4: autenticación. Se expone como la interfaz; FirebaseAuth se inicializa
    // solo (FirebaseInitProvider) con los datos de google-services.json.
    val autenticacion: AuthRepository by lazy { FirebaseAuthRepository(FirebaseAuth.getInstance()) }
}
