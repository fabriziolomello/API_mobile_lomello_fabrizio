package com.fabrizio.recordatorios

import com.fabrizio.recordatorios.data.LocalFirstReminderRepository
import com.fabrizio.recordatorios.data.local.ReminderDao
import com.fabrizio.recordatorios.data.local.ReminderEntity
import com.fabrizio.recordatorios.data.local.SyncStatus
import com.fabrizio.recordatorios.model.Categoria
import com.fabrizio.recordatorios.model.Estado
import com.fabrizio.recordatorios.model.Prioridad
import com.fabrizio.recordatorios.model.Recordatorio
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalFirstReminderRepositoryTest {

    private val dao = FakeReminderDao()
    private var ahora = 1_000L
    private val repositorio = LocalFirstReminderRepository(
        dao = dao,
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

// DAO en memoria que imita las consultas de ReminderDao (sin Room ni Android)
private class FakeReminderDao : ReminderDao {
    val filas = MutableStateFlow<List<ReminderEntity>>(emptyList())
    private var proximoId = 1L

    private fun visibles() = filas.map { lista -> lista.filter { it.syncStatus != SyncStatus.PENDING_DELETE } }

    override suspend fun insert(reminder: ReminderEntity): Long {
        require(filas.value.none { it.clientId == reminder.clientId }) { "clientId duplicado" }
        val id = proximoId++
        filas.value = filas.value + reminder.copy(id = id)
        return id
    }

    override suspend fun update(reminder: ReminderEntity) {
        filas.value = filas.value.map { if (it.id == reminder.id) reminder else it }
    }

    override suspend fun delete(reminder: ReminderEntity) {
        filas.value = filas.value.filterNot { it.id == reminder.id }
    }

    override fun observeAll(): Flow<List<ReminderEntity>> = visibles()
    override fun observeById(id: Long): Flow<ReminderEntity?> = visibles().map { l -> l.find { it.id == id } }
    override fun observeByCompleted(completed: Boolean) = visibles().map { l -> l.filter { it.isCompleted == completed } }
    override fun observeByPriority(priority: Prioridad) = visibles().map { l -> l.filter { it.priority == priority } }
    override suspend fun getById(id: Long) = filas.value.find { it.id == id }
    override suspend fun getByClientId(clientId: String) = filas.value.find { it.clientId == clientId }
    override suspend fun getByRemoteId(remoteId: String) = filas.value.find { it.remoteId == remoteId }
    override suspend fun getPendingSync() = filas.value.filter {
        it.syncStatus in setOf(SyncStatus.PENDING_CREATE, SyncStatus.PENDING_UPDATE, SyncStatus.PENDING_DELETE)
    }.sortedBy { it.updatedAt }
    override suspend fun getFailed() = filas.value.filter { it.syncStatus == SyncStatus.FAILED }
}
