package com.fabrizio.recordatorios.ui.ajustes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fabrizio.recordatorios.RecordatoriosApplication
import com.fabrizio.recordatorios.data.preferencias.CriterioOrden
import com.fabrizio.recordatorios.data.preferencias.FiltroPrioridad
import com.fabrizio.recordatorios.data.preferencias.PreferenciasRepository
import com.fabrizio.recordatorios.data.preferencias.PreferenciasUsuario
import com.fabrizio.recordatorios.data.preferencias.TemaApp
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AjustesViewModel(
    private val preferencias: PreferenciasRepository
) : ViewModel() {

    // DataStore es la fuente de verdad: la pantalla solo refleja lo que está guardado
    val uiState: StateFlow<AjustesUiState> = preferencias.preferencias
        .map<PreferenciasUsuario, AjustesUiState> { AjustesUiState.Listo(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AjustesUiState.Cargando
        )

    // Cada cambio se escribe con una función suspend dentro de viewModelScope:
    // no bloquea el hilo principal y, al guardarse, el Flow emite el valor nuevo.
    fun onTemaSeleccionado(tema: TemaApp) {
        viewModelScope.launch { preferencias.guardarTema(tema) }
    }

    fun onMostrarCompletadosChange(mostrar: Boolean) {
        viewModelScope.launch { preferencias.guardarMostrarCompletados(mostrar) }
    }

    fun onOrdenSeleccionado(orden: CriterioOrden) {
        viewModelScope.launch { preferencias.guardarOrden(orden) }
    }

    fun onFiltroPrioridadSeleccionado(filtro: FiltroPrioridad) {
        viewModelScope.launch { preferencias.guardarFiltroPrioridad(filtro) }
    }

    fun onSimularTemaInvalido() {
        viewModelScope.launch { preferencias.guardarTemaInvalidoParaPrueba() }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as RecordatoriosApplication
                AjustesViewModel(app.container.preferencias)
            }
        }
    }
}

sealed interface AjustesUiState {
    data object Cargando : AjustesUiState
    data class Listo(val preferencias: PreferenciasUsuario) : AjustesUiState
}
