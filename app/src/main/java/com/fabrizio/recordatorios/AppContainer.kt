package com.fabrizio.recordatorios

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.withTransaction
import com.fabrizio.recordatorios.data.LocalFirstReminderRepository
import com.fabrizio.recordatorios.data.ReminderRepository
import com.fabrizio.recordatorios.data.auth.AuthRepository
import com.fabrizio.recordatorios.data.auth.CierreSesion
import com.fabrizio.recordatorios.data.auth.FirebaseAuthRepository
import com.fabrizio.recordatorios.data.cloud.FirestoreReminderDataSource
import com.fabrizio.recordatorios.data.cloud.crearFirestoreSinCachePersistente
import com.fabrizio.recordatorios.data.local.AppDatabase
import com.fabrizio.recordatorios.data.preferencias.PreferenciasRepository
import com.fabrizio.recordatorios.data.preferencias.PreferenciasRepositoryDataStore
import com.fabrizio.recordatorios.data.remote.SimuladorFallas
import com.fabrizio.recordatorios.data.sync.AndroidConnectivityObserver
import com.fabrizio.recordatorios.data.sync.ConnectivityObserver
import com.fabrizio.recordatorios.data.sync.SyncManager
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Delegate a nivel de archivo: garantiza UNA sola instancia de DataStore por proceso.
// Tener dos instancias sobre el mismo archivo hace que DataStore lance una excepción.
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "preferencias_usuario")

// Inyección de dependencias manual: un único lugar donde se crean las dependencias
// de la app. Las factories de los ViewModel las toman de acá.
class AppContainer(context: Context) {

    // Se guarda el applicationContext para no retener una Activity
    private val appContext = context.applicationContext

    // Scope que vive mientras viva el proceso.
    // SupervisorJob: si una tarea falla, el scope sigue sirviendo para las siguientes.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Scope hijo SOLO para la sincronización: al cerrar sesión se cancelan sus tareas
    // (sync en curso y escucha de la reconexión) sin afectar al resto de la app.
    private val syncJob = SupervisorJob(appScope.coroutineContext.job)
    private val syncScope = CoroutineScope(appScope.coroutineContext + syncJob)

    // Base de datos singleton (ver AppDatabase.getInstance)
    private val database: AppDatabase by lazy { AppDatabase.getInstance(appContext) }

    val preferencias: PreferenciasRepository by lazy {
        PreferenciasRepositoryDataStore(appContext.dataStore)
    }

    val conectividad: ConnectivityObserver by lazy { AndroidConnectivityObserver(appContext) }

    // Herramienta de debug de la Actividad 3: simula fallas en el interceptor de OkHttp
    // (MockAPI). Desde la Actividad 4 la sincronización usa Firestore, así que ya no la
    // afecta; se mantiene porque el menú de debug del listado la sigue usando.
    val simuladorFallas: SimuladorFallas by lazy { SimuladorFallas() }

    // Actividad 4: Firestore con caché persistente desactivada. Este es el ÚNICO lugar que
    // obtiene la instancia, y la configura antes de cualquier uso (ver data/cloud).
    // MockAPI/Retrofit (data/remote) quedan en el código pero ya no se cablean.
    private val firestore by lazy { crearFirestoreSinCachePersistente() }

    val syncManager: SyncManager by lazy {
        SyncManager(
            dao = database.reminderDao(),
            nube = FirestoreReminderDataSource(firestore),
            conectividad = conectividad,
            appScope = syncScope,
            enTransaccion = { bloque -> database.withTransaction { bloque() } },
            registrarSincronizacion = preferencias::guardarUltimaSincronizacion,
            // El sync trabaja solo con las filas del usuario logueado
            usuarioActual = { autenticacion.currentUser.first()?.uid },
            log = { mensaje -> Log.w("SyncManager", mensaje) }
        )
    }

    // Se expone como la interfaz: el resto de la app no conoce la implementación
    val repositorio: ReminderRepository by lazy {
        LocalFirstReminderRepository(database.reminderDao(), syncManager, preferencias, autenticacion)
    }

    // Actividad 4: autenticación. Se expone como la interfaz; FirebaseAuth se inicializa
    // solo (FirebaseInitProvider) con los datos de google-services.json.
    val autenticacion: AuthRepository by lazy { FirebaseAuthRepository(FirebaseAuth.getInstance()) }

    val cierreSesion: CierreSesion by lazy {
        CierreSesion(
            dao = database.reminderDao(),
            syncManager = syncManager,
            detenerSincronizacion = ::detenerSincronizacion,
            // clearAllTables bloquea el hilo: se ejecuta en IO
            borrarBaseLocal = { withContext(Dispatchers.IO) { database.clearAllTables() } },
            preferencias = preferencias,
            auth = autenticacion
        )
    }

    // La sincronización sigue a la sesión: con usuario escucha la reconexión y sincroniza;
    // sin usuario se detiene (así no se bajan datos a Room mientras no hay nadie logueado).
    fun vincularSincronizacionASesion() {
        appScope.launch {
            autenticacion.currentUser
                .map { usuario -> usuario != null }
                .distinctUntilChanged()
                .collect { conSesion -> if (conSesion) syncManager.iniciar() else detenerSincronizacion() }
        }
    }

    // Cancela cada tarea de sync y espera a que termine (el Mutex se libera en su finally)
    private suspend fun detenerSincronizacion() {
        syncJob.children.toList().onEach { it.cancel() }.joinAll()
    }
}
