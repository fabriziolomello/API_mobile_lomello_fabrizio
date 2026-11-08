package com.fabrizio.recordatorios.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fabrizio.recordatorios.RecordatoriosApplication
import com.fabrizio.recordatorios.data.auth.AuthError
import com.fabrizio.recordatorios.data.auth.AuthRepository
import com.fabrizio.recordatorios.data.auth.AuthUser
import com.fabrizio.recordatorios.data.auth.ValidacionAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthViewModel(
    private val repositorio: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        // Firebase es la fuente de verdad de la sesión: el estado refleja cada cambio
        viewModelScope.launch {
            repositorio.currentUser.collect { usuario ->
                _uiState.update { it.copy(usuario = usuario, sesionVerificada = true) }
            }
        }
    }

    fun register(email: String, password: String) =
        autenticar(email, password) { correo, contrasena -> repositorio.register(correo, contrasena) }

    fun login(email: String, password: String) =
        autenticar(email, password) { correo, contrasena -> repositorio.login(correo, contrasena) }

    fun logout() {
        repositorio.logout()
        // El usuario pasa a null cuando currentUser emite el cierre de sesión
        _uiState.update { it.copy(error = null) }
    }

    fun limpiarError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun autenticar(
        email: String,
        password: String,
        operacion: suspend (String, String) -> Result<AuthUser>
    ) {
        if (_uiState.value.cargando) return // evita dobles envíos

        val correo = email.trim()
        // Validación local: si falla, no se llama a Firebase
        ValidacionAuth.validar(correo, password)?.let { errorLocal ->
            _uiState.update { it.copy(error = errorLocal) }
            return
        }

        _uiState.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            val resultado = operacion(correo, password)
            _uiState.update { estado ->
                resultado.fold(
                    onSuccess = { usuario -> estado.copy(cargando = false, usuario = usuario) },
                    onFailure = { e ->
                        estado.copy(cargando = false, error = e as? AuthError ?: AuthError.Desconocido(e))
                    }
                )
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as RecordatoriosApplication
                AuthViewModel(app.container.autenticacion)
            }
        }
    }
}
