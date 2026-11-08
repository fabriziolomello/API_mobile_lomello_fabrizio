package com.fabrizio.recordatorios.ui.listado

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fabrizio.recordatorios.RecordatoriosApplication
import com.fabrizio.recordatorios.data.ReminderRepository
import com.fabrizio.recordatorios.data.auth.CierreSesion
import com.fabrizio.recordatorios.data.preferencias.PreferenciasRepository
import com.fabrizio.recordatorios.data.preferencias.PreferenciasUsuario
import com.fabrizio.recordatorios.data.remote.FallaSimulada
import com.fabrizio.recordatorios.data.remote.SimuladorFallas
import com.fabrizio.recordatorios.data.sync.ConnectivityObserver
import com.fabrizio.recordatorios.data.sync.EstadoSincronizacion
import com.fabrizio.recordatorios.model.FormatoFecha
import com.fabrizio.recordatorios.model.Recordatorio
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ListadoViewModel(
    private val repositorio: ReminderRepository,
    private val preferencias: PreferenciasRepository,
    conectividad: ConnectivityObserver,
    private val simuladorFallas: SimuladorFallas,
    private val cierreSesion: CierreSesion
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

    // Estado de la sincronización para la barra superior y el Snackbar.
    // Combina 4 fuentes: estado del sync, pendientes (Room), último sync (DataStore) y la red.
    val sincronizacion: StateFlow<SincronizacionUi> = combine(
        repositorio.estadoSincronizacion,
        repositorio.cambiosPendientes,
        repositorio.ultimaSincronizacion,
        conectividad.estaConectado
    ) { estado, pendientes, ultima, conectado ->
        SincronizacionUi(
            sincronizando = estado == EstadoSincronizacion.Sincronizando,
            conectado = conectado,
            pendientes = pendientes,
            ultimaSincronizacion = ultima?.let(FormatoFecha::formatearFechaHora),
            error = estado as? EstadoSincronizacion.Error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SincronizacionUi()
    )

    // Solo debug: falla de red forzada por el simulador
    val fallaSimulada: StateFlow<FallaSimulada> = simuladorFallas.falla

    // --- Eventos que llegan desde la UI ---

    fun onReintentar() = reintentos.update { it + 1 }

    // Pull-to-refresh o botón: sync manual. Si falla, el error llega por el estado "sincronizacion"
    fun onSincronizar() {
        viewModelScope.launch { repositorio.sincronizar() }
    }

    // Acción "Reintentar" del Snackbar: también reintenta los cambios rechazados
    fun onReintentarSincronizacion() {
        viewModelScope.launch { repositorio.reintentarFallidos() }
    }

    fun onFallaSimuladaSeleccionada(falla: FallaSimulada) = simuladorFallas.cambiarFalla(falla)

    // --- Cierre de sesión ---
    // Al terminar NO se navega desde acá: logout() cambia el Flow de sesión y
    // la navegación vuelve sola al login con el historial limpio.

    private val _cierreSesion = MutableStateFlow<CierreSesionUi>(CierreSesionUi.Inactivo)
    val cierreSesionUi: StateFlow<CierreSesionUi> = _cierreSesion.asStateFlow()

    fun onCerrarSesionClick() {
        if (_cierreSesion.value == CierreSesionUi.Inactivo) _cierreSesion.value = CierreSesionUi.Confirmando
    }

    fun onCancelarCierreSesion() {
        // Mientras se sincroniza o se borra no se puede cancelar (los diálogos no lo ofrecen)
        val estado = _cierreSesion.value
        if (estado != CierreSesionUi.Sincronizando && estado != CierreSesionUi.Cerrando) {
            _cierreSesion.value = CierreSesionUi.Inactivo
        }
    }

    // Paso 1: intenta subir lo pendiente. Si queda algo sin subir, pregunta de nuevo.
    fun onConfirmarCierreSesion() {
        _cierreSesion.value = CierreSesionUi.Sincronizando
        viewModelScope.launch {
            val sinSincronizar = try {
                cierreSesion.sincronizarAntesDeSalir()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Si falla el conteo, se avisa igual en vez de borrar a ciegas
                _cierreSesion.value = CierreSesionUi.Error
                return@launch
            }
            if (sinSincronizar > 0) {
                _cierreSesion.value = CierreSesionUi.AvisoPendientes(sinSincronizar)
            } else {
                cerrarSesion()
            }
        }
    }

    // Paso 2 (con pendientes): el usuario acepta perderlos
    fun onConfirmarCierreConPendientes() {
        viewModelScope.launch { cerrarSesion() }
    }

    private suspend fun cerrarSesion() {
        _cierreSesion.value = CierreSesionUi.Cerrando
        try {
            cierreSesion.cerrarSesion()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // La limpieza falló antes del logout: la sesión sigue abierta y se puede reintentar
            _cierreSesion.value = CierreSesionUi.Error
        }
    }

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
                ListadoViewModel(
                    repositorio = app.container.repositorio,
                    preferencias = app.container.preferencias,
                    conectividad = app.container.conectividad,
                    simuladorFallas = app.container.simuladorFallas,
                    cierreSesion = app.container.cierreSesion
                )
            }
        }
    }
}
