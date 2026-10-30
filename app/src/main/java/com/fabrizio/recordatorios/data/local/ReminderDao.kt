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
}
