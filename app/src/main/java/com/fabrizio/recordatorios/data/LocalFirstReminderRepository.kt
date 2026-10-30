package com.fabrizio.recordatorios.data

import com.fabrizio.recordatorios.data.local.ReminderDao
import com.fabrizio.recordatorios.data.local.ReminderEntity
import com.fabrizio.recordatorios.data.local.SyncStatus
import com.fabrizio.recordatorios.data.mapper.aplicarEdicion
import com.fabrizio.recordatorios.data.mapper.toDomain
import com.fabrizio.recordatorios.data.mapper.toEntity
import com.fabrizio.recordatorios.model.Estado
import com.fabrizio.recordatorios.model.Recordatorio
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.UUID

// Repositorio local-first: Room es la única fuente de verdad de la UI.
// Toda escritura se guarda primero en Room marcada como pendiente; la
// sincronización con el servidor (Fase 4) se hace después, sin bloquear al usuario.
class LocalFirstReminderRepository(
    private val dao: ReminderDao,
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
        return dao.insert(entidad)
    }

    override suspend fun actualizar(recordatorio: Recordatorio) {
        val actual = buscarVisible(recordatorio.id)
        // update (no insert): se modifica la misma fila, nunca se duplica
        dao.update(actual.aplicarEdicion(recordatorio, reloj()))
    }

    override suspend fun cambiarCompletado(id: Long, completado: Boolean) {
        val actual = buscarVisible(id)
        if (actual.isCompleted == completado) return
        val editado = actual.toDomain().copy(
            estado = if (completado) Estado.Completado else Estado.Pendiente
        )
        dao.update(actual.aplicarEdicion(editado, reloj()))
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
        }
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
