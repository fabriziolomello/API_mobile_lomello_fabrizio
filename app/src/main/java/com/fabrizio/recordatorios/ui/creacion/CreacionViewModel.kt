package com.fabrizio.recordatorios.ui.creacion

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
import com.fabrizio.recordatorios.model.Categoria
import com.fabrizio.recordatorios.model.Estado
import com.fabrizio.recordatorios.model.Prioridad
import com.fabrizio.recordatorios.model.Recordatorio
import com.fabrizio.recordatorios.model.ValidacionRecordatorio
import com.fabrizio.recordatorios.navigation.Rutas
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CreacionViewModel(
    savedStateHandle: SavedStateHandle,
    private val repositorio: ReminderRepository
) : ViewModel() {

    // Con id en la ruta (editar/{id}) el formulario edita; sin id, crea uno nuevo
    private val idEdicion: Long? = savedStateHandle.get<Long>(Rutas.ARG_ID)

    // Recordatorio original al editar: conserva los datos que el formulario no muestra (estado)
    private var original: Recordatorio? = null

    private val _uiState = MutableStateFlow(
        CreacionUiState(modoEdicion = idEdicion != null, cargando = idEdicion != null)
    )
    val uiState: StateFlow<CreacionUiState> = _uiState.asStateFlow()

    init {
        if (idEdicion != null) cargarParaEditar(idEdicion)
    }

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

    // null = quitar la fecha límite
    fun onFechaLimiteSeleccionada(fecha: Long?) = _uiState.update {
        it.copy(fechaLimite = fecha, envio = limpiarError(it.envio))
    }

    fun onGuardar() {
        val estado = _uiState.value
        // Doble chequeo: aunque el botón esté deshabilitado, el ViewModel no confía en la UI
        if (!estado.puedeGuardar) return

        _uiState.update { it.copy(envio = EstadoEnvio.Enviando) }

        // viewModelScope: la corrutina se cancela sola si el ViewModel se destruye
        viewModelScope.launch {
            try {
                val datos = Recordatorio(
                    id = idEdicion ?: 0, // al crear, Room asigna el id real
                    titulo = estado.titulo.trim(),
                    categoria = requireNotNull(estado.categoria),
                    prioridad = requireNotNull(estado.prioridad),
                    estado = original?.estado ?: Estado.Pendiente,
                    descripcion = estado.descripcion.trim().ifBlank { null },
                    fechaLimite = estado.fechaLimite
                )
                // Ambas operaciones son locales (Room): terminan aunque no haya conexión
                if (idEdicion == null) repositorio.crear(datos) else repositorio.actualizar(datos)
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

    // Lee el recordatorio una sola vez y completa el formulario con sus valores
    private fun cargarParaEditar(id: Long) {
        viewModelScope.launch {
            val recordatorio = repositorio.observarRecordatorio(id).first()
            original = recordatorio
            _uiState.update {
                if (recordatorio == null) {
                    it.copy(cargando = false, envio = EstadoEnvio.Error("No encontramos el recordatorio a editar."))
                } else {
                    it.copy(
                        cargando = false,
                        titulo = recordatorio.titulo,
                        descripcion = recordatorio.descripcion.orEmpty(),
                        categoria = recordatorio.categoria,
                        prioridad = recordatorio.prioridad,
                        fechaLimite = recordatorio.fechaLimite
                    ).conValidez()
                }
            }
        }
    }

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
                CreacionViewModel(createSavedStateHandle(), app.container.repositorio)
            }
        }
    }
}
