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

class SyncManagerTest {

    private val dao = FakeReminderDao()
    private val remoto = FakeRemoteDataSource()
    private val prefs = FakePreferenciasRepository()
    private val syncManager = SyncManager(
        dao = dao,
        remoto = remoto,
        conectividad = FakeConnectivityObserver(conectado = true),
        appScope = CoroutineScope(Dispatchers.Unconfined),
        enTransaccion = { it() },
        registrarSincronizacion = prefs::guardarUltimaSincronizacion,
        reloj = { 5_000L }
    )

    private suspend fun crearLocal(clientId: String = "uuid-1", status: SyncStatus = SyncStatus.PENDING_CREATE, remoteId: String? = null) =
        dao.insert(
            ReminderEntity(
                clientId = clientId, remoteId = remoteId, title = "Estudiar", category = Categoria.Estudio,
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
        val fila = dao.getById(id)!!
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
        assertEquals("77", dao.getById(id)!!.remoteId)
        assertEquals(SyncStatus.SYNCED, dao.getById(id)!!.syncStatus)
    }

    @Test
    fun timeoutDespuesDeEnviarYReintentoNoDuplica() = runBlocking {
        val id = crearLocal()
        remoto.perderRespuestaDelPost = true

        val primero = syncManager.sincronizar()
        assertEquals(SyncResult.Fallo(DataResult.Timeout), primero)
        assertEquals(SyncStatus.PENDING_CREATE, dao.getById(id)!!.syncStatus) // sigue pendiente

        remoto.perderRespuestaDelPost = false
        syncManager.sincronizar() // reconexión / reintento

        assertEquals(1, remoto.posts)
        assertEquals(1, remoto.servidor.size)
        assertEquals(1, dao.filas.value.size)
        assertEquals(SyncStatus.SYNCED, dao.getById(id)!!.syncStatus)
    }

    @Test
    fun errorDeServidorConservaLosDatosPendientes() = runBlocking {
        val id = crearLocal()
        remoto.falla = DataResult.ErrorServidor(500)

        val resultado = syncManager.sincronizar()

        assertEquals(SyncResult.Fallo(DataResult.ErrorServidor(500)), resultado)
        assertEquals(SyncStatus.PENDING_CREATE, dao.getById(id)!!.syncStatus)
        assertTrue(syncManager.estado.value is EstadoSincronizacion.Error)
        assertNull(prefs.ultima.value)
    }

    @Test
    fun errorDeClienteMarcaFailedYReintentarLoVuelveAPendiente() = runBlocking {
        val id = crearLocal()
        remoto.falla = DataResult.ErrorCliente(400)

        syncManager.sincronizar()
        assertEquals(SyncStatus.FAILED, dao.getById(id)!!.syncStatus)

        // FAILED no se reenvía solo
        remoto.falla = null
        syncManager.sincronizar()
        assertEquals(0, remoto.posts)

        // Reintento manual
        dao.retryFailed(id)
        syncManager.sincronizar()
        assertEquals(SyncStatus.SYNCED, dao.getById(id)!!.syncStatus)
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
        assertNull(dao.getById(id))
    }

    @Test
    fun pullAgregaLosRemotosYRespetaLosCambiosLocalesPendientes() = runBlocking {
        val idLocal = crearLocal(clientId = "uuid-1", status = SyncStatus.PENDING_UPDATE, remoteId = "1")
        remoto.falla = null
        remoto.servidor += ReminderDto(id = "1", clientId = "uuid-1", title = "Versión vieja del servidor")
        remoto.servidor += ReminderDto(id = "2", clientId = "uuid-2", title = "Creado en otro dispositivo", category = "TRABAJO")

        syncManager.sincronizar()

        // El PUT envió la versión local y el pull no la pisó
        assertEquals("Estudiar", dao.getById(idLocal)!!.title)
        assertEquals("Estudiar", remoto.servidor.first { it.id == "1" }.title)
        // El registro remoto nuevo se insertó una sola vez
        val nuevo = dao.getByRemoteId("2")!!
        assertEquals(SyncStatus.SYNCED, nuevo.syncStatus)
        assertEquals(Categoria.Trabajo, nuevo.category)
        assertEquals(2, dao.filas.value.size)
    }
}
