package com.fabrizio.recordatorios.data.mapper

import com.fabrizio.recordatorios.data.local.ReminderEntity
import com.fabrizio.recordatorios.data.local.SyncStatus
import com.fabrizio.recordatorios.model.Estado
import com.fabrizio.recordatorios.model.EstadoSync
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
    fechaLimite = dueDate,
    estadoSync = syncStatus.toEstadoSync()
)

// Los 5 estados técnicos se resumen en 3 para el usuario
fun SyncStatus.toEstadoSync(): EstadoSync = when (this) {
    SyncStatus.PENDING_CREATE, SyncStatus.PENDING_UPDATE, SyncStatus.PENDING_DELETE -> EstadoSync.PENDIENTE
    SyncStatus.SYNCED -> EstadoSync.SINCRONIZADO
    SyncStatus.FAILED -> EstadoSync.ERROR
}

// Alta local: el clientId (UUID) se genera una sola vez y nunca cambia.
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

// Edición local: se copian los datos editables y se CONSERVA la identidad
// (id, clientId, remoteId, createdAt). Así nunca se crea un registro nuevo.
fun ReminderEntity.aplicarEdicion(editado: Recordatorio, ahora: Long): ReminderEntity = copy(
    title = editado.titulo,
    description = editado.descripcion,
    category = editado.categoria,
    priority = editado.prioridad,
    isCompleted = editado.estado == Estado.Completado,
    dueDate = editado.fechaLimite,
    updatedAt = ahora,
    syncStatus = estadoTrasEdicionLocal()
)

// Regla de pendientes al editar:
// - Si el servidor todavía no lo conoce (sin remoteId), sigue siendo una creación:
//   se enviará un solo POST con los datos más nuevos, no un POST y después un PUT.
// - Si ya existe en el servidor, queda como actualización pendiente (PUT).
fun ReminderEntity.estadoTrasEdicionLocal(): SyncStatus =
    if (remoteId == null) SyncStatus.PENDING_CREATE else SyncStatus.PENDING_UPDATE
