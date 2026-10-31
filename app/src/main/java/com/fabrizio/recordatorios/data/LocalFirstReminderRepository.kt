package com.fabrizio.recordatorios.data

import com.fabrizio.recordatorios.data.local.ReminderDao
import com.fabrizio.recordatorios.data.local.ReminderEntity
import com.fabrizio.recordatorios.data.local.SyncStatus
import com.fabrizio.recordatorios.data.mapper.aplicarEdicion
import com.fabrizio.recordatorios.data.mapper.toDomain
import com.fabrizio.recordatorios.data.mapper.toEntity
import com.fabrizio.recordatorios.data.preferencias.PreferenciasRepository
import com.fabrizio.recordatorios.data.sync.EstadoSincronizacion
import com.fabrizio.recordatorios.data.sync.SyncManager
import com.fabrizio.recordatorios.data.sync.SyncResult
import com.fabrizio.recordatorios.model.Estado
import com.fabrizio.recordatorios.model.Recordatorio
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.UUID

// Repositorio local-first: coordina Room (fuente de verdad de la UI), DataStore
// (hora del último sync) y el servidor (a través del SyncManager).
// Toda escritura se guarda primero en Room marcada como pendiente y termina al
// instante; el envío al servidor ocurre después, sin bloquear al usuario.
class LocalFirstReminderRepository(
    private val dao: ReminderDao,
    private val syncManager: SyncManager,
    private val preferencias: PreferenciasRepository,
    // Inyectables para poder testear con valores fijos
    private val reloj: () -> Long = System::currentTimeMillis,
    private val generarClientId: () -> String = { UUID.randomUUID().toString() }
) : ReminderRepository {

    override fun observarRecordatorios(): Flow<List<Recordatorio>> =
        dao.observeAll()
            .map { entidades -> entidades.map { it.toDomain() } }
            .distinctUntilChanged()

    override fun observarRecordatorio(id: Long): Flow<Recordatorio?> =
        dao.observeById(id)
            .map { it?.toDomain() }
            .distinctUntilChanged()

    override suspend fun crear(recordatorio: Recordatorio): Long {
        // clientId: clave de idempotencia, generada UNA vez al crear
        val entidad = recordatorio.toEntity(clientId = generarClientId(), ahora = reloj())
        return dao.insert(entidad).also { syncManager.solicitarSync() }
    }

    override suspend fun actualizar(recordatorio: Recordatorio) {
        val actual = buscarVisible(recordatorio.id)
        // update (no insert): se modifica la misma fila, nunca se duplica
        dao.update(actual.aplicarEdicion(recordatorio, reloj()))
        syncManager.solicitarSync()
    }

    override suspend fun cambiarCompletado(id: Long, completado: Boolean) {
        val actual = buscarVisible(id)
        if (actual.isCompleted == completado) return
        val editado = actual.toDomain().copy(
            estado = if (completado) Estado.Completado else Estado.Pendiente
        )
        dao.update(actual.aplicarEdicion(editado, reloj()))
        syncManager.solicitarSync()
    }

    override suspend fun eliminar(id: Long) {
        val actual = dao.getById(id) ?: return
        if (actual.remoteId == null) {
            // Nunca llegó al servidor: no hay nada que avisarle, se borra ya
            dao.delete(actual)
        } else {
            // Existe en el servidor: borrado lógico. La UI lo oculta al instante
            // y la fila se elimina de verdad cuando el servidor confirme el DELETE.
            dao.update(actual.copy(syncStatus = SyncStatus.PENDING_DELETE, updatedAt = reloj()))
            syncManager.solicitarSync()
        }
    }

    // --- Sincronización ---

    override val estadoSincronizacion: StateFlow<EstadoSincronizacion> = syncManager.estado

    override val cambiosPendientes: Flow<Int> = dao.observePendingCount()

    override val ultimaSincronizacion: Flow<Long?> = preferencias.ultimaSincronizacion

    override suspend fun sincronizar(): SyncResult = syncManager.sincronizar()

    override suspend fun reintentar(id: Long) {
        dao.retryFailed(id)
        syncManager.sincronizar()
    }

    override suspend fun reintentarFallidos() {
        dao.retryAllFailed()
        syncManager.sincronizar()
    }

    // Un recordatorio marcado para borrar no se puede editar
    private suspend fun buscarVisible(id: Long): ReminderEntity {
        val entidad = dao.getById(id)
        if (entidad == null || entidad.syncStatus == SyncStatus.PENDING_DELETE) {
            throw RecordatorioNoEncontradoException(id)
        }
        return entidad
    }
}
