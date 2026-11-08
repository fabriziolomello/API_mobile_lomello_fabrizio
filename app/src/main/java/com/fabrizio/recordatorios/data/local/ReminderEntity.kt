package com.fabrizio.recordatorios.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.fabrizio.recordatorios.model.Categoria
import com.fabrizio.recordatorios.model.Prioridad

// Tabla de recordatorios. Es la ÚNICA fuente de verdad que observa la UI.
// Cada fila pertenece a un usuario (user_id = uid de Firebase Auth): todas las
// consultas del DAO filtran por esa columna, así los datos quedan aislados por cuenta.
@Entity(
    tableName = "reminders",
    indices = [
        // Único: impide que el mismo recordatorio quede dos veces en la base
        Index(value = ["client_id"], unique = true),
        // Todas las consultas filtran por usuario
        Index(value = ["user_id"]),
        Index(value = ["remote_id"]),
        Index(value = ["sync_status"])
    ]
)
data class ReminderEntity(
    // Id local estable: lo usa la navegación (detalle/{id}) y no cambia al sincronizar
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // UUID generado en el dispositivo al crear. Es la clave de idempotencia:
    // identifica al recordatorio aunque todavía no tenga remoteId.
    @ColumnInfo(name = "client_id")
    val clientId: String,

    // Dueño del recordatorio (uid de Firebase Auth). Sin valor por defecto a propósito:
    // ninguna fila se puede crear sin decir de quién es.
    @ColumnInfo(name = "user_id")
    val userId: String,

    // Id que asigna MockAPI; null hasta que se confirma la creación.
    // Solo lo usa la sincronización con MockAPI (PUT/DELETE reminders/{id}).
    // Firestore va a usar client_id como id del documento, así que para ese remoto
    // esta columna queda siempre null y se ignora.
    @ColumnInfo(name = "remote_id")
    val remoteId: String? = null,

    val title: String,
    val description: String? = null,
    val category: Categoria,
    val priority: Prioridad,

    @ColumnInfo(name = "is_completed")
    val isCompleted: Boolean = false,

    // Fechas como epoch millis: se ordenan bien en SQL y no requieren conversión
    @ColumnInfo(name = "due_date")
    val dueDate: Long? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    // Última modificación local o remota (se usa para ordenar pendientes y resolver conflictos)
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus = SyncStatus.PENDING_CREATE
)
