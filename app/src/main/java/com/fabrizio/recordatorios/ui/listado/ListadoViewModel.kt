package com.fabrizio.recordatorios.ui.listado

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fabrizio.recordatorios.RecordatoriosApplication
import com.fabrizio.recordatorios.data.EscenarioListado
import com.fabrizio.recordatorios.data.RecordatorioRepository
import com.fabrizio.recordatorios.data.Simulacion
import com.fabrizio.recordatorios.data.preferencias.PreferenciasRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ListadoViewModel(
    private val repositorio: RecordatorioRepository,
    private val simulacion: Simulacion,
    preferencias: PreferenciasRepository
) : ViewModel() {

    // Resultado "crudo" de la carga, sin filtros. Privado y mutable adentro.
    private val _estadoCarga = MutableStateFlow<ListadoUiState>(ListadoUiState.Cargando)

    // Lo que ve la UI: la carga combinada con las preferencias (orden y filtros).
    // Si el usuario cambia una preferencia, la lista se reacomoda sola sin volver a cargar.
    val uiState: StateFlow<ListadoUiState> =
        combine(_estadoCarga, preferencias.preferencias) { estado, prefs ->
            if (estado is ListadoUiState.Datos) {
                val visibles = estado.recordatorios.aplicarPreferencias(prefs)
                if (visibles.isEmpty()) ListadoUiState.SinCoincidencias
                else ListadoUiState.Datos(visibles)
            } else {
                estado
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ListadoUiState.Cargando
        )

    // Opciones de simulación (solo lectura) para el menú de debug
    val escenario: StateFlow<EscenarioListado> = simulacion.escenario
    val fallarGuardado: StateFlow<Boolean> = simulacion.fallarGuardado

    private var cargaJob: Job? = null

    init {
        cargar()
        // Si se guarda un recordatorio nuevo, se vuelve a cargar la lista
        viewModelScope.launch {
            repositorio.cambios.collect { cargar() }
        }
    }

    // --- Eventos que llegan desde la UI ---

    fun onReintentar() = cargar()

    fun onEscenarioSeleccionado(escenario: EscenarioListado) {
        simulacion.cambiarEscenario(escenario)
        cargar()
    }

    fun onFallarGuardadoChange(fallar: Boolean) = simulacion.cambiarFallarGuardado(fallar)

    // --- Lógica interna ---

    private fun cargar() {
        // Si había una carga en curso (por ejemplo "carga sin fin"), se cancela
        cargaJob?.cancel()
        cargaJob = viewModelScope.launch {
            _estadoCarga.update { ListadoUiState.Cargando }
            try {
                val lista = repositorio.obtenerTodos()
                _estadoCarga.update {
                    if (lista.isEmpty()) ListadoUiState.Vacio else ListadoUiState.Datos(lista)
                }
            } catch (e: CancellationException) {
                throw e // la cancelación no es un error: se deja propagar
            } catch (e: Exception) {
                _estadoCarga.update {
                    ListadoUiState.Error("No pudimos cargar tus recordatorios. Revisá tu conexión e intentá de nuevo.")
                }
            }
        }
    }

    companion object {
        // Factory simple: toma el repositorio de la Application y lo pasa al constructor
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as RecordatoriosApplication
                ListadoViewModel(app.container.repositorio, app.container.simulacion, app.container.preferencias)
            }
        }
    }
}
