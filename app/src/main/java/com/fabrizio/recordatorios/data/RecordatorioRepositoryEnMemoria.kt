package com.fabrizio.recordatorios.data

import com.fabrizio.recordatorios.model.Recordatorio
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update

// Implementación en memoria: los datos viven mientras la app esté abierta.
// Parte de los recordatorios simulados de la Actividad 1.
class RecordatorioRepositoryEnMemoria(
    private val simulacion: Simulacion,
    datosIniciales: List<Recordatorio> = recordatoriosSimulados
) : RecordatorioRepository {

    // Lista inmutable dentro de un StateFlow: cada cambio crea una lista nueva
    private val recordatorios = MutableStateFlow(datosIniciales)

    private val _cambios = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val cambios: Flow<Unit> = _cambios.asSharedFlow()

    override suspend fun obtenerTodos(): List<Recordatorio> {
        // delay suspende la corrutina sin bloquear el hilo principal
        delay(simulacion.demoraCargaMs)
        return when (simulacion.escenario.value) {
            EscenarioListado.NORMAL -> recordatorios.value
            EscenarioListado.VACIO -> emptyList()
            EscenarioListado.ERROR -> throw IllegalStateException("Error simulado al cargar")
            // Se queda "cargando" hasta que se cancele (por ejemplo, al reintentar)
            EscenarioListado.CARGA_INFINITA -> awaitCancellation()
        }
    }

    override suspend fun obtenerPorId(id: Long): Recordatorio? {
        delay(simulacion.demoraCargaMs / 3)
        return recordatorios.value.find { it.id == id }
    }

    override suspend fun guardar(recordatorio: Recordatorio): Recordatorio {
        delay(simulacion.demoraGuardadoMs)
        if (simulacion.fallarGuardado.value) {
            throw IllegalStateException("Error simulado al guardar")
        }
        // Se asigna el próximo id disponible
        val nuevo = recordatorio.copy(id = (recordatorios.value.maxOfOrNull { it.id } ?: 0) + 1)
        recordatorios.update { lista -> lista + nuevo }
        _cambios.tryEmit(Unit)
        return nuevo
    }
}
