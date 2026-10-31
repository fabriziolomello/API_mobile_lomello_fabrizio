package com.fabrizio.recordatorios.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.fabrizio.recordatorios.model.Prioridad
import kotlinx.coroutines.flow.Flow

// Acceso a la tabla reminders. Solo lo usa el repositorio: los composables
// y los ViewModel nunca llegan hasta acá.
// Las consultas para la UI excluyen PENDING_DELETE: un borrado se ve al instante
// aunque todavía no se haya confirmado en el servidor.
@Dao
interface ReminderDao {

    // --- Escritura (suspend: Room la ejecuta fuera del hilo principal) ---

    // ABORT: si el clientId ya existe, falla en lugar de duplicar o pisar
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(reminder: ReminderEntity): Long

    @Update
    suspend fun update(reminder: ReminderEntity)

    @Delete
    suspend fun delete(reminder: ReminderEntity)

    // --- Lectura observable (Flow): emite de nuevo cada vez que cambia la tabla ---

    @Query("SELECT * FROM reminders WHERE sync_status != 'PENDING_DELETE' ORDER BY id DESC")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE id = :id AND sync_status != 'PENDING_DELETE'")
    fun observeById(id: Long): Flow<ReminderEntity?>

    @Query(
        "SELECT * FROM reminders WHERE is_completed = :completed " +
            "AND sync_status != 'PENDING_DELETE' ORDER BY id DESC"
    )
    fun observeByCompleted(completed: Boolean): Flow<List<ReminderEntity>>

    // El parámetro Prioridad se convierte con Converters igual que la columna
    @Query(
        "SELECT * FROM reminders WHERE priority = :priority " +
            "AND sync_status != 'PENDING_DELETE' ORDER BY id DESC"
    )
    fun observeByPriority(priority: Prioridad): Flow<List<ReminderEntity>>

    // --- Lectura puntual (suspend), para el repositorio y la sincronización ---

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: Long): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE client_id = :clientId")
    suspend fun getByClientId(clientId: String): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE remote_id = :remoteId")
    suspend fun getByRemoteId(remoteId: String): ReminderEntity?

    // Operaciones a enviar al servidor, en el orden en que se hicieron
    @Query(
        "SELECT * FROM reminders " +
            "WHERE sync_status IN ('PENDING_CREATE', 'PENDING_UPDATE', 'PENDING_DELETE') " +
            "ORDER BY updated_at ASC"
    )
    suspend fun getPendingSync(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE sync_status = 'FAILED'")
    suspend fun getFailed(): List<ReminderEntity>

    // Filas con remoteId (para detectar las que se borraron en el servidor)
    @Query("SELECT * FROM reminders WHERE remote_id IS NOT NULL")
    suspend fun getWithRemoteId(): List<ReminderEntity>

    // Cantidad de cambios sin enviar (indicador en la UI)
    @Query(
        "SELECT COUNT(*) FROM reminders " +
            "WHERE sync_status IN ('PENDING_CREATE', 'PENDING_UPDATE', 'PENDING_DELETE')"
    )
    fun observePendingCount(): Flow<Int>

    // --- Actualizaciones condicionales para la sincronización ---
    // Todas comparan updated_at con el valor que se ENVIÓ al servidor: si el usuario
    // editó el recordatorio mientras la petición viajaba, no se pisa su cambio
    // (devuelven 0 filas y el registro sigue pendiente para la próxima vuelta).

    @Query(
        "UPDATE reminders SET remote_id = :remoteId, sync_status = 'SYNCED' " +
            "WHERE id = :id AND updated_at = :updatedAtEnviado"
    )
    suspend fun markSynced(id: Long, remoteId: String, updatedAtEnviado: Long): Int

    // El servidor confirmó la creación pero la fila cambió mientras tanto:
    // se guarda el remoteId igual (para no volver a crearla) y queda como actualización.
    @Query(
        "UPDATE reminders SET remote_id = :remoteId, " +
            "sync_status = CASE WHEN sync_status = 'PENDING_CREATE' THEN 'PENDING_UPDATE' ELSE sync_status END " +
            "WHERE id = :id"
    )
    suspend fun assignRemoteId(id: Long, remoteId: String): Int

    @Query("UPDATE reminders SET sync_status = 'FAILED' WHERE id = :id AND updated_at = :updatedAtEnviado")
    suspend fun markFailed(id: Long, updatedAtEnviado: Long): Int

    // El servidor ya no tiene el registro (404 en un PUT): se vuelve a crear.
    // Mantiene el clientId, así la verificación previa al POST evita duplicados.
    @Query("UPDATE reminders SET remote_id = NULL, sync_status = 'PENDING_CREATE' WHERE id = :id")
    suspend fun resetToPendingCreate(id: Long)

    // Solo borra si sigue marcada para borrar (confirmación del DELETE remoto)
    @Query("DELETE FROM reminders WHERE id = :id AND sync_status = 'PENDING_DELETE'")
    suspend fun deleteIfPendingDelete(id: Long): Int

    // Reintento manual de operaciones rechazadas (4xx): vuelven a quedar pendientes
    @Query(
        "UPDATE reminders SET sync_status = " +
            "CASE WHEN remote_id IS NULL THEN 'PENDING_CREATE' ELSE 'PENDING_UPDATE' END " +
            "WHERE id = :id AND sync_status = 'FAILED'"
    )
    suspend fun retryFailed(id: Long): Int

    @Query(
        "UPDATE reminders SET sync_status = " +
            "CASE WHEN remote_id IS NULL THEN 'PENDING_CREATE' ELSE 'PENDING_UPDATE' END " +
            "WHERE sync_status = 'FAILED'"
    )
    suspend fun retryAllFailed(): Int
}
