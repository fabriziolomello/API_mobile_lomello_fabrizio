package com.fabrizio.recordatorios.data

import com.fabrizio.recordatorios.model.Recordatorio
import kotlinx.coroutines.flow.Flow

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
}

// El recordatorio pedido no existe (o ya fue eliminado)
class RecordatorioNoEncontradoException(id: Long) :
    IllegalStateException("No existe el recordatorio $id")
