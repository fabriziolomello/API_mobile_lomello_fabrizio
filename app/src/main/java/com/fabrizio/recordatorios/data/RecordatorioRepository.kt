package com.fabrizio.recordatorios.data

import com.fabrizio.recordatorios.model.Recordatorio
import kotlinx.coroutines.flow.Flow

// Contrato de acceso a datos. Los ViewModel dependen de esta interfaz,
// no de la implementación, así mañana se puede cambiar por Room o una API sin tocarlos.
interface RecordatorioRepository {

    // Obtiene todos los recordatorios (puede lanzar excepción si falla)
    suspend fun obtenerTodos(): List<Recordatorio>

    // Devuelve null si no existe un recordatorio con ese id
    suspend fun obtenerPorId(id: Long): Recordatorio?

    // Guarda un recordatorio nuevo y lo devuelve con su id asignado
    suspend fun guardar(recordatorio: Recordatorio): Recordatorio

    // Avisa cada vez que cambian los datos (por ejemplo, después de guardar),
    // para que el listado se actualice al volver de la pantalla de creación.
    val cambios: Flow<Unit>
}
