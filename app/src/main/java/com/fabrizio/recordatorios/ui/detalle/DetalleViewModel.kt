package com.fabrizio.recordatorios.ui.detalle

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fabrizio.recordatorios.RecordatoriosApplication
import com.fabrizio.recordatorios.data.ReminderRepository
import com.fabrizio.recordatorios.navigation.Rutas
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DetalleViewModel(
    savedStateHandle: SavedStateHandle,
    private val repositorio: ReminderRepository
) : ViewModel() {

    // Navigation Compose deja los argumentos de la ruta en el SavedStateHandle.
    // Solo viaja el id; el recordatorio completo se observa en el repositorio.
    private val id: Long? = savedStateHandle.get<Long>(Rutas.ARG_ID)

    // Se marca ANTES de borrar, para pasar directo a "Eliminado" y no mostrar
    // "No encontrado" por un instante cuando Room deja de emitir el registro.
    private val eliminado = MutableStateFlow(false)

    // Mensaje puntual para un Snackbar (null = nada que mostrar)
    private val _mensaje = MutableStateFlow<String?>(null)
    val mensaje: StateFlow<String?> = _mensaje.asStateFlow()

    // Al observar (y no leer una sola vez), si el recordatorio se edita
    // o se completa, el detalle se actualiza solo.
    val uiState: StateFlow<DetalleUiState> =
        (if (id == null) flowOf(DetalleUiState.NoEncontrado) else {
            combine(repositorio.observarRecordatorio(id), eliminado) { recordatorio, fueEliminado ->
                when {
                    fueEliminado -> DetalleUiState.Eliminado
                    recordatorio == null -> DetalleUiState.NoEncontrado
                    else -> DetalleUiState.Encontrado(recordatorio)
                }
            }
        }).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DetalleUiState.Cargando
        )

    // --- Eventos que llegan desde la UI ---

    fun onCompletadoChange(completado: Boolean) {
        val idActual = id ?: return
        ejecutar("No se pudo actualizar el estado. Intentá de nuevo.") {
            repositorio.cambiarCompletado(idActual, completado)
        }
    }

    fun onEliminarConfirmado() {
        val idActual = id ?: return
        eliminado.update { true }
        ejecutar("No se pudo eliminar el recordatorio. Intentá de nuevo.", alFallar = { eliminado.update { false } }) {
            repositorio.eliminar(idActual)
        }
    }

    // Reintento manual de un cambio que el servidor rechazó (estado "Error al sincronizar")
    fun onReintentarSync() {
        val idActual = id ?: return
        ejecutar("No se pudo reintentar la sincronización.") { repositorio.reintentar(idActual) }
    }

    fun onMensajeMostrado() = _mensaje.update { null }

    // --- Auxiliares ---

    private fun ejecutar(
        mensajeError: String,
        alFallar: () -> Unit = {},
        accion: suspend () -> Unit
    ) {
        viewModelScope.launch {
            try {
                accion()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                alFallar()
                _mensaje.update { mensajeError }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as RecordatoriosApplication
                DetalleViewModel(createSavedStateHandle(), app.container.repositorio)
            }
        }
    }
}
