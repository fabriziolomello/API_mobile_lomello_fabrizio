package com.fabrizio.recordatorios

import com.fabrizio.recordatorios.data.LocalFirstReminderRepository
import com.fabrizio.recordatorios.data.local.SyncStatus
import com.fabrizio.recordatorios.data.sync.SyncManager
import com.fabrizio.recordatorios.model.Categoria
import com.fabrizio.recordatorios.model.Estado
import com.fabrizio.recordatorios.model.Prioridad
import com.fabrizio.recordatorios.model.Recordatorio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalFirstReminderRepositoryTest {

    private val dao = FakeReminderDao()
    private var ahora = 1_000L
    // Sin conexión: los cambios quedan solo en Room (no se dispara ningún sync)
    private val syncManager = SyncManager(
        dao = dao,
        remoto = FakeRemoteDataSource(),
        conectividad = FakeConnectivityObserver(conectado = false),
        appScope = CoroutineScope(Dispatchers.Unconfined),
        enTransaccion = { it() },
        registrarSincronizacion = {}
    )
    private val repositorio = LocalFirstReminderRepository(
        dao = dao,
        syncManager = syncManager,
        preferencias = FakePreferenciasRepository(),
        reloj = { ahora },
        generarClientId = { "uuid-${dao.filas.value.size + 1}" }
    )

    private val nuevo = Recordatorio(
        id = 0,
        titulo = "Estudiar Room",
        categoria = Categoria.Estudio,
        prioridad = Prioridad.Alta,
        estado = Estado.Pendiente
    )

    @Test
    fun crearGuardaPendienteDeCrearConClientId() = runBlocking {
        val id = repositorio.crear(nuevo)
        val fila = dao.getById(id)!!
        assertEquals(SyncStatus.PENDING_CREATE, fila.syncStatus)
        assertEquals("uuid-1", fila.clientId)
        assertNull(fila.remoteId)
    }

    @Test
    fun editarAlgoNuncaSincronizadoSigueSiendoCreacionYNoDuplica() = runBlocking {
        val id = repositorio.crear(nuevo)
        ahora = 2_000L
        repositorio.actualizar(nuevo.copy(id = id, titulo = "Estudiar Room a fondo"))

        val fila = dao.getById(id)!!
        assertEquals(1, dao.filas.value.size)
        assertEquals(SyncStatus.PENDING_CREATE, fila.syncStatus)
        assertEquals("uuid-1", fila.clientId)
        assertEquals(2_000L, fila.updatedAt)
    }

    @Test
    fun editarAlgoSincronizadoQuedaPendienteDeActualizar() = runBlocking {
        val id = repositorio.crear(nuevo)
        dao.update(dao.getById(id)!!.copy(remoteId = "42", syncStatus = SyncStatus.SYNCED))

        repositorio.cambiarCompletado(id, true)

        val fila = dao.getById(id)!!
        assertEquals(SyncStatus.PENDING_UPDATE, fila.syncStatus)
        assertTrue(fila.isCompleted)
        assertEquals("42", fila.remoteId)
    }

    @Test
    fun eliminarSinRemoteIdBorraLaFila() = runBlocking {
        val id = repositorio.crear(nuevo)
        repositorio.eliminar(id)
        assertNull(dao.getById(id))
    }

    @Test
    fun eliminarConRemoteIdEsBorradoLogicoYSeOcultaEnLaUi() = runBlocking {
        val id = repositorio.crear(nuevo)
        dao.update(dao.getById(id)!!.copy(remoteId = "42", syncStatus = SyncStatus.SYNCED))

        repositorio.eliminar(id)

        assertEquals(SyncStatus.PENDING_DELETE, dao.getById(id)!!.syncStatus)
        assertTrue(repositorio.observarRecordatorios().first().isEmpty())
        assertNull(repositorio.observarRecordatorio(id).first())
    }
}
