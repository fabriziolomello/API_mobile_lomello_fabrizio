package com.fabrizio.recordatorios

import com.fabrizio.recordatorios.data.local.ReminderEntity
import com.fabrizio.recordatorios.data.local.SyncStatus
import com.fabrizio.recordatorios.data.remote.DataResult
import com.fabrizio.recordatorios.data.remote.ReminderDto
import com.fabrizio.recordatorios.data.sync.EstadoSincronizacion
import com.fabrizio.recordatorios.data.sync.SyncManager
import com.fabrizio.recordatorios.data.sync.SyncResult
import com.fabrizio.recordatorios.model.Categoria
import com.fabrizio.recordatorios.model.Prioridad
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val UID = "uid-ana"
private const val OTRO_UID = "uid-beto"

class SyncManagerTest {

    private val dao = FakeReminderDao()
    private val remoto = FakeRemoteDataSource()
    private val prefs = FakePreferenciasRepository()
    private var usuario: String? = UID
    private val syncManager = SyncManager(
        dao = dao,
        remoto = remoto,
        conectividad = FakeConnectivityObserver(conectado = true),
        appScope = CoroutineScope(Dispatchers.Unconfined),
        enTransaccion = { it() },
        registrarSincronizacion = prefs::guardarUltimaSincronizacion,
        usuarioActual = { usuario },
        reloj = { 5_000L }
    )

    private suspend fun crearLocal(
        clientId: String = "uuid-1",
        status: SyncStatus = SyncStatus.PENDING_CREATE,
        remoteId: String? = null,
        userId: String = UID
    ) =
        dao.insert(
            ReminderEntity(
                clientId = clientId, userId = userId, remoteId = remoteId, title = "Estudiar", category = Categoria.Estudio,
                priority = Prioridad.Alta, createdAt = 1_000, updatedAt = 1_000, syncStatus = status
            )
        )

    @Test
    fun creacionPendienteSeEnviaUnaVezYGuardaElRemoteId() = runBlocking {
        val id = crearLocal()

        val resultado = syncManager.sincronizar()
        syncManager.sincronizar() // segunda vuelta: no debe reenviar nada

        assertEquals(SyncResult.Exito(enviados = 1, recibidos = 0, rechazados = 0), resultado)
        assertEquals(1, remoto.posts)
        assertEquals(1, remoto.servidor.size)
        val fila = dao.getById(UID, id)!!
        assertEquals(SyncStatus.SYNCED, fila.syncStatus)
        assertEquals("1", fila.remoteId)
        assertEquals(1, dao.filas.value.size) // misma fila, sin duplicar
        assertEquals(5_000L, prefs.ultima.value) // último sync guardado (DataStore)
    }

    @Test
    fun siElServidorYaTieneElClientIdNoSeCreaOtro() = runBlocking {
        // Un POST anterior llegó al servidor pero la respuesta se perdió
        remoto.servidor += ReminderDto(id = "77", clientId = "uuid-1", title = "Estudiar")
        val id = crearLocal()

        syncManager.sincronizar()

        assertEquals(0, remoto.posts)
        assertEquals(1, remoto.servidor.size)
        assertEquals("77", dao.getById(UID, id)!!.remoteId)
        assertEquals(SyncStatus.SYNCED, dao.getById(UID, id)!!.syncStatus)
    }

    @Test
    fun timeoutDespuesDeEnviarYReintentoNoDuplica() = runBlocking {
        val id = crearLocal()
        remoto.perderRespuestaDelPost = true

        val primero = syncManager.sincronizar()
        assertEquals(SyncResult.Fallo(DataResult.Timeout), primero)
        assertEquals(SyncStatus.PENDING_CREATE, dao.getById(UID, id)!!.syncStatus) // sigue pendiente

        remoto.perderRespuestaDelPost = false
        syncManager.sincronizar() // reconexión / reintento

        assertEquals(1, remoto.posts)
        assertEquals(1, remoto.servidor.size)
        assertEquals(1, dao.filas.value.size)
        assertEquals(SyncStatus.SYNCED, dao.getById(UID, id)!!.syncStatus)
    }

    @Test
    fun errorDeServidorConservaLosDatosPendientes() = runBlocking {
        val id = crearLocal()
        remoto.falla = DataResult.ErrorServidor(500)

        val resultado = syncManager.sincronizar()

        assertEquals(SyncResult.Fallo(DataResult.ErrorServidor(500)), resultado)
        assertEquals(SyncStatus.PENDING_CREATE, dao.getById(UID, id)!!.syncStatus)
        assertTrue(syncManager.estado.value is EstadoSincronizacion.Error)
        assertNull(prefs.ultima.value)
    }

    @Test
    fun errorDeClienteMarcaFailedYReintentarLoVuelveAPendiente() = runBlocking {
        val id = crearLocal()
        remoto.falla = DataResult.ErrorCliente(400)

        syncManager.sincronizar()
        assertEquals(SyncStatus.FAILED, dao.getById(UID, id)!!.syncStatus)

        // FAILED no se reenvía solo
        remoto.falla = null
        syncManager.sincronizar()
        assertEquals(0, remoto.posts)

        // Reintento manual
        dao.retryFailed(UID, id)
        syncManager.sincronizar()
        assertEquals(SyncStatus.SYNCED, dao.getById(UID, id)!!.syncStatus)
        assertEquals(1, remoto.posts)
    }

    @Test
    fun soloCorreUnSyncALaVez() = runBlocking {
        crearLocal()
        remoto.bloqueoPost = CompletableDeferred()

        // El primer sync queda frenado en medio del POST (tiene el Mutex)
        val primero = async(start = CoroutineStart.UNDISPATCHED) { syncManager.sincronizar() }
        // Un segundo evento (por ejemplo, otra reconexión) no lanza otro en paralelo
        val segundo = syncManager.sincronizar()
        assertEquals(SyncResult.EnCurso, segundo)

        remoto.bloqueoPost!!.complete(Unit)
        primero.await()
        assertEquals(1, remoto.posts)
        assertEquals(1, remoto.servidor.size)
    }

    @Test
    fun borradoPendienteSeEnviaYLuegoSeQuitaDeRoom() = runBlocking {
        remoto.servidor += ReminderDto(id = "9", clientId = "uuid-1", title = "Estudiar")
        val id = crearLocal(status = SyncStatus.PENDING_DELETE, remoteId = "9")

        syncManager.sincronizar()

        assertTrue(remoto.servidor.isEmpty())
        assertNull(dao.getById(UID, id))
    }

    @Test
    fun pullAgregaLosRemotosYRespetaLosCambiosLocalesPendientes() = runBlocking {
        val idLocal = crearLocal(clientId = "uuid-1", status = SyncStatus.PENDING_UPDATE, remoteId = "1")
        remoto.falla = null
        remoto.servidor += ReminderDto(id = "1", clientId = "uuid-1", title = "Versión vieja del servidor")
        remoto.servidor += ReminderDto(id = "2", clientId = "uuid-2", title = "Creado en otro dispositivo", category = "TRABAJO")

        syncManager.sincronizar()

        // El PUT envió la versión local y el pull no la pisó
        assertEquals("Estudiar", dao.getById(UID, idLocal)!!.title)
        assertEquals("Estudiar", remoto.servidor.first { it.id == "1" }.title)
        // El registro remoto nuevo se insertó una sola vez
        val nuevo = dao.getByRemoteId(UID, "2")!!
        assertEquals(SyncStatus.SYNCED, nuevo.syncStatus)
        assertEquals(Categoria.Trabajo, nuevo.category)
        assertEquals(2, dao.filas.value.size)
    }

    // --- Aislamiento por usuario ---

    @Test
    fun sinSesionNoSeEnviaNiSeBajaNada() = runBlocking {
        val id = crearLocal()
        remoto.servidor += ReminderDto(id = "5", clientId = "uuid-5", title = "Remoto")
        usuario = null

        val resultado = syncManager.sincronizar()

        assertEquals(SyncResult.SinSesion, resultado)
        assertEquals(0, remoto.posts)
        assertEquals(SyncStatus.PENDING_CREATE, dao.getById(UID, id)!!.syncStatus)
        assertEquals(1, dao.filas.value.size)
        assertNull(prefs.ultima.value)
    }

    @Test
    fun soloSeEnvianLosPendientesDelUsuarioActualYElPullQuedaASuNombre() = runBlocking {
        crearLocal(clientId = "uuid-ana")
        val idBeto = crearLocal(clientId = "uuid-beto", userId = OTRO_UID)
        remoto.servidor += ReminderDto(id = "50", clientId = "uuid-50", title = "Creado en otro dispositivo")

        syncManager.sincronizar()

        // Solo se envió el de Ana; el de Beto sigue pendiente y sin tocar
        assertEquals(1, remoto.posts)
        assertEquals(SyncStatus.PENDING_CREATE, dao.getById(OTRO_UID, idBeto)!!.syncStatus)
        // Lo que bajó del servidor quedó a nombre de Ana
        assertEquals(UID, dao.getByRemoteId(UID, "50")!!.userId)
        assertNull(dao.getByRemoteId(OTRO_UID, "50"))
    }
}
