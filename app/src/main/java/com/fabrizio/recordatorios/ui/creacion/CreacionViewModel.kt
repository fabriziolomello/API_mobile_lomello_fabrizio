package com.fabrizio.recordatorios.ui.creacion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fabrizio.recordatorios.RecordatoriosApplication
import com.fabrizio.recordatorios.data.RecordatorioRepository
import com.fabrizio.recordatorios.model.Categoria
import com.fabrizio.recordatorios.model.Estado
import com.fabrizio.recordatorios.model.Prioridad
import com.fabrizio.recordatorios.model.Recordatorio
import com.fabrizio.recordatorios.model.ValidacionRecordatorio
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CreacionViewModel(
    private val repositorio: RecordatorioRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreacionUiState())
    val uiState: StateFlow<CreacionUiState> = _uiState.asStateFlow()

    // --- Eventos del formulario: validan y actualizan con copy ---
    // Al editar se limpia un error de guardado anterior (vuelve a Inactivo).

    fun onTituloChange(titulo: String) = _uiState.update {
        it.copy(
            titulo = titulo,
            errorTitulo = ValidacionRecordatorio.validarTitulo(titulo),
            envio = limpiarError(it.envio)
        ).conValidez()
    }

    fun onDescripcionChange(descripcion: String) = _uiState.update {
        it.copy(
            descripcion = descripcion,
            errorDescripcion = ValidacionRecordatorio.validarDescripcion(descripcion),
            envio = limpiarError(it.envio)
        ).conValidez()
    }

    fun onCategoriaSeleccionada(categoria: Categoria) = _uiState.update {
        it.copy(
            categoria = categoria,
            errorCategoria = ValidacionRecordatorio.validarCategoria(categoria),
            envio = limpiarError(it.envio)
        ).conValidez()
    }

    fun onPrioridadSeleccionada(prioridad: Prioridad) = _uiState.update {
        it.copy(
            prioridad = prioridad,
            errorPrioridad = ValidacionRecordatorio.validarPrioridad(prioridad),
            envio = limpiarError(it.envio)
        ).conValidez()
    }

    fun onGuardar() {
        val estado = _uiState.value
        // Doble chequeo: aunque el botón esté deshabilitado, el ViewModel no confía en la UI
        if (!estado.puedeGuardar) return

        _uiState.update { it.copy(envio = EstadoEnvio.Enviando) }

        // viewModelScope: la corrutina se cancela sola si el ViewModel se destruye
        viewModelScope.launch {
            try {
                repositorio.guardar(
                    Recordatorio(
                        id = 0, // el repositorio asigna el id real
                        titulo = estado.titulo.trim(),
                        categoria = requireNotNull(estado.categoria),
                        prioridad = requireNotNull(estado.prioridad),
                        estado = Estado.Pendiente,
                        descripcion = estado.descripcion.trim().ifBlank { null }
                    )
                )
                _uiState.update { it.copy(envio = EstadoEnvio.Exito) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(envio = EstadoEnvio.Error("No se pudo guardar el recordatorio. Intentá de nuevo."))
                }
            }
        }
    }

    // La UI avisa que ya navegó: el éxito se consume para no navegar dos veces
    // (por ejemplo, al recomponer o rotar la pantalla).
    fun onExitoConsumido() = _uiState.update {
        if (it.envio == EstadoEnvio.Exito) it.copy(envio = EstadoEnvio.Inactivo) else it
    }

    // --- Auxiliares ---

    // Recalcula si el formulario completo es válido con las reglas del modelo
    private fun CreacionUiState.conValidez(): CreacionUiState = copy(
        formularioValido = ValidacionRecordatorio.validarTitulo(titulo) == null &&
            ValidacionRecordatorio.validarDescripcion(descripcion) == null &&
            ValidacionRecordatorio.validarCategoria(categoria) == null &&
            ValidacionRecordatorio.validarPrioridad(prioridad) == null
    )

    private fun limpiarError(envio: EstadoEnvio): EstadoEnvio =
        if (envio is EstadoEnvio.Error) EstadoEnvio.Inactivo else envio

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as RecordatoriosApplication
                CreacionViewModel(app.repositorio)
            }
        }
    }
}
