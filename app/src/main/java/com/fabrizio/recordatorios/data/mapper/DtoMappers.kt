package com.fabrizio.recordatorios.data.mapper

import com.fabrizio.recordatorios.data.local.ReminderEntity
import com.fabrizio.recordatorios.data.local.SyncStatus
import com.fabrizio.recordatorios.data.remote.ReminderDto
import com.fabrizio.recordatorios.model.Categoria
import com.fabrizio.recordatorios.model.Prioridad

// Mappers Entity <-> DTO. Los DTO se transforman SIEMPRE antes de entrar a Room:
// la base nunca guarda un objeto de red tal cual.

// Lo que se envía al servidor. id = remoteId (null en un POST).
fun ReminderEntity.toDto(): ReminderDto = ReminderDto(
    id = remoteId,
    clientId = clientId,
    title = title,
    description = description,
    category = category.codigo,
    priority = priority.codigo,
    completed = isCompleted,
    dueDate = dueDate,
    createdAt = createdAt,
    updatedAt = updatedAt
)

// Registro que llega del servidor y no existe localmente.
// userId: el usuario de la sesión que hizo el pull (MockAPI no guarda dueño).
// Devuelve null si el DTO no es válido (sin id): se descarta en vez de romper.
fun ReminderDto.toEntity(userId: String, ahora: Long): ReminderEntity? {
    val remoteId = id?.takeIf { it.isNotBlank() } ?: return null
    return ReminderEntity(
        // Registros creados fuera de la app (sin clientId) reciben uno derivado del id remoto
        clientId = clientId?.takeIf { it.isNotBlank() } ?: "remote-$remoteId",
        userId = userId,
        remoteId = remoteId,
        title = title.ifBlank { "(Sin título)" },
        description = description?.takeIf { it.isNotBlank() },
        category = Categoria.desdeCodigo(category),
        priority = Prioridad.desdeCodigo(priority),
        isCompleted = completed,
        dueDate = dueDate,
        createdAt = createdAt ?: ahora,
        updatedAt = updatedAt ?: ahora,
        syncStatus = SyncStatus.SYNCED
    )
}

// Actualiza una fila SINCRONIZADA con los datos del servidor (gana el servidor),
// conservando la identidad local (id, clientId, userId).
fun ReminderEntity.actualizarDesde(dto: ReminderDto): ReminderEntity = copy(
    title = dto.title.ifBlank { title },
    description = dto.description?.takeIf { it.isNotBlank() },
    category = Categoria.desdeCodigo(dto.category),
    priority = Prioridad.desdeCodigo(dto.priority),
    isCompleted = dto.completed,
    dueDate = dto.dueDate,
    updatedAt = dto.updatedAt ?: updatedAt,
    syncStatus = SyncStatus.SYNCED
)
