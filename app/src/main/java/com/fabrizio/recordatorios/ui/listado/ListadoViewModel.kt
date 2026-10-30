package com.fabrizio.recordatorios.ui.listado

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fabrizio.recordatorios.RecordatoriosApplication
import com.fabrizio.recordatorios.data.ReminderRepository
import com.fabrizio.recordatorios.data.preferencias.PreferenciasRepository
import com.fabrizio.recordatorios.data.preferencias.PreferenciasUsuario
import com.fabrizio.recordatorios.model.Recordatorio
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class ListadoViewModel(
    private val repositorio: ReminderRepository,
    private val preferencias: PreferenciasRepository
) : ViewModel() {

    // Cada reintento incrementa el contador y vuelve a suscribirse a Room
    private val reintentos = MutableStateFlow(0)

    // La UI observa Room (vía repositorio) combinado con las preferencias.
    // Cualquier alta, edición o borrado en la base llega acá solo, sin recargar.
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ListadoUiState> = reintentos
        .flatMapLatest {
            combine(repositorio.observarRecordatorios(), preferencias.preferencias, ::construirEstado)
                // Si falla la lectura local, se muestra un error con opción de reintentar
                .catch { emit(ListadoUiState.Error("No pudimos leer tus recordatorios guardados. Intentá de nuevo.")) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ListadoUiState.Cargando
        )

    // --- Eventos que llegan desde la UI ---

    fun onReintentar() = reintentos.update { it + 1 }

    // --- Lógica interna ---

    private fun construirEstado(
        recordatorios: List<Recordatorio>,
        prefs: PreferenciasUsuario
    ): ListadoUiState {
        if (recordatorios.isEmpty()) return ListadoUiState.Vacio
        val visibles = recordatorios.aplicarPreferencias(prefs)
        return if (visibles.isEmpty()) ListadoUiState.SinCoincidencias else ListadoUiState.Datos(visibles)
    }

    companion object {
        // Factory simple: toma las dependencias del AppContainer y las pasa al constructor
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as RecordatoriosApplication
                ListadoViewModel(app.container.repositorio, app.container.preferencias)
            }
        }
    }
}
