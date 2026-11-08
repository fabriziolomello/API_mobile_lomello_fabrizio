package com.fabrizio.recordatorios.data

import com.fabrizio.recordatorios.data.sync.EstadoSincronizacion
import com.fabrizio.recordatorios.data.sync.SyncResult
import com.fabrizio.recordatorios.model.Recordatorio
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

// Contrato de acceso a los recordatorios. Los ViewModel dependen de esta interfaz:
// no saben si los datos vienen de Room, de la red o de ambos.
interface ReminderRepository {

    // Flujos observables: emiten de nuevo cada vez que cambia la base local,
    // así la UI se actualiza sola (sin "recargar").
    fun observarRecordatorios(): Flow<List<Recordatorio>>

    // Emite null si no existe o fue eliminado
    fun observarRecordatorio(id: Long): Flow<Recordatorio?>

    // Escrituras local-first: se aplican primero en Room (quedan pendientes de
    // sincronizar) y terminan aunque no haya conexión.

    // Devuelve el id local asignado
    suspend fun crear(recordatorio: Recordatorio): Long

    suspend fun actualizar(recordatorio: Recordatorio)

    suspend fun cambiarCompletado(id: Long, completado: Boolean)

    suspend fun eliminar(id: Long)

    // --- Sincronización con el servidor ---

    val estadoSincronizacion: StateFlow<EstadoSincronizacion>

    // Cantidad de cambios locales todavía no enviados
    val cambiosPendientes: Flow<Int>

    // Hora del último sync exitoso (guardada en DataStore)
    val ultimaSincronizacion: Flow<Long?>

    // Sync manual (pull-to-refresh / botón). Nunca lanza excepciones de red:
    // devuelve un SyncResult y los datos locales quedan intactos si falla.
    suspend fun sincronizar(): SyncResult

    // Vuelve a dejar pendientes las operaciones rechazadas (FAILED) y sincroniza
    suspend fun reintentar(id: Long)
    suspend fun reintentarFallidos()
}

// El recordatorio pedido no existe (o ya fue eliminado, o es de otro usuario)
class RecordatorioNoEncontradoException(id: Long) :
    IllegalStateException("No existe el recordatorio $id")

// Se intentó escribir sin una sesión iniciada. Nunca se guarda una fila sin dueño.
class SesionRequeridaException :
    IllegalStateException("No hay una sesión iniciada: no se pueden guardar recordatorios sin usuario")
