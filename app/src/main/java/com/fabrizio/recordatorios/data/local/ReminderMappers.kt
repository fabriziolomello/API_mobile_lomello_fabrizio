package com.fabrizio.recordatorios.data.local

import com.fabrizio.recordatorios.model.Estado
import com.fabrizio.recordatorios.model.Recordatorio

// Mappers Entity <-> dominio. La UI solo conoce Recordatorio; los campos de
// sincronización (clientId, remoteId, syncStatus) quedan dentro de la capa de datos.

fun ReminderEntity.toDomain(): Recordatorio = Recordatorio(
    id = id,
    titulo = title,
    categoria = category,
    prioridad = priority,
    estado = if (isCompleted) Estado.Completado else Estado.Pendiente,
    descripcion = description,
    fechaLimite = dueDate
)

// Alta local: el clientId (UUID) se genera una sola vez acá y nunca cambia.
// Queda PENDING_CREATE hasta que el servidor confirme.
fun Recordatorio.toEntity(clientId: String, ahora: Long): ReminderEntity = ReminderEntity(
    clientId = clientId,
    title = titulo,
    description = descripcion,
    category = categoria,
    priority = prioridad,
    isCompleted = estado == Estado.Completado,
    dueDate = fechaLimite,
    createdAt = ahora,
    updatedAt = ahora,
    syncStatus = SyncStatus.PENDING_CREATE
)
