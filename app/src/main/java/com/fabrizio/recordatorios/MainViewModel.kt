package com.fabrizio.recordatorios

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fabrizio.recordatorios.data.auth.AuthRepository
import com.fabrizio.recordatorios.data.preferencias.PreferenciasRepository
import com.fabrizio.recordatorios.data.preferencias.TemaApp
import com.fabrizio.recordatorios.ui.auth.EstadoSesion
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

// ViewModel de la Activity: expone el tema elegido y el estado de la sesión (para la navegación)
class MainViewModel(
    preferencias: PreferenciasRepository,
    autenticacion: AuthRepository
) : ViewModel() {

    // null = todavía no se leyó DataStore (evita mostrar un tema y enseguida cambiarlo)
    val tema: StateFlow<TemaApp?> = preferencias.preferencias
        .map { it.tema }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    // La navegación reacciona a este Flow: emite en cada inicio o cierre de sesión,
    // incluso si la sesión se invalida desde afuera (cuenta deshabilitada, token revocado).
    val sesion: StateFlow<EstadoSesion> = autenticacion.currentUser
        .map { usuario -> usuario?.let { EstadoSesion.Autenticado(it.uid) } ?: EstadoSesion.SinSesion }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = EstadoSesion.Verificando
        )

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as RecordatoriosApplication
                MainViewModel(app.container.preferencias, app.container.autenticacion)
            }
        }
    }
}
