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
import com.fabrizio.recordatorios.data.RecordatorioRepository
import com.fabrizio.recordatorios.navigation.Rutas
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DetalleViewModel(
    savedStateHandle: SavedStateHandle,
    private val repositorio: RecordatorioRepository
) : ViewModel() {

    // Navigation Compose deja los argumentos de la ruta en el SavedStateHandle.
    // Solo viaja el id; el recordatorio completo se busca en el repositorio.
    private val id: Long? = savedStateHandle.get<Long>(Rutas.ARG_ID)

    private val _uiState = MutableStateFlow<DetalleUiState>(DetalleUiState.Cargando)
    val uiState: StateFlow<DetalleUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val recordatorio = id?.let { repositorio.obtenerPorId(it) }
            _uiState.update {
                if (recordatorio != null) DetalleUiState.Encontrado(recordatorio)
                else DetalleUiState.NoEncontrado
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as RecordatoriosApplication
                DetalleViewModel(createSavedStateHandle(), app.repositorio)
            }
        }
    }
}
