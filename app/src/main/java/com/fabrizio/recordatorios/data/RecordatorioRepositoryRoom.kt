package com.fabrizio.recordatorios.data

import com.fabrizio.recordatorios.data.local.ReminderDao
import com.fabrizio.recordatorios.data.local.toDomain
import com.fabrizio.recordatorios.data.local.toEntity
import com.fabrizio.recordatorios.model.Recordatorio
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

// Implementación persistente: los recordatorios viven en Room y sobreviven
// al cierre de la app. Reemplaza a la versión en memoria de la Actividad 2
// sin que los ViewModel cambien, porque dependen de la interfaz.
class RecordatorioRepositoryRoom(
    private val dao: ReminderDao
) : RecordatorioRepository {

    override suspend fun obtenerTodos(): List<Recordatorio> =
        dao.observeAll().first().map { it.toDomain() }

    override suspend fun obtenerPorId(id: Long): Recordatorio? =
        dao.observeById(id).first()?.toDomain()

    override suspend fun guardar(recordatorio: Recordatorio): Recordatorio {
        // El clientId se genera una única vez, al crear, y acompaña al registro para siempre
        val entidad = recordatorio.toEntity(
            clientId = UUID.randomUUID().toString(),
            ahora = System.currentTimeMillis()
        )
        val idLocal = dao.insert(entidad)
        return entidad.copy(id = idLocal).toDomain()
    }

    // Room vuelve a emitir cada vez que cambia la tabla.
    // drop(1) descarta la emisión inicial: solo interesan los cambios posteriores.
    override val cambios: Flow<Unit> = dao.observeAll().drop(1).map { }
}
