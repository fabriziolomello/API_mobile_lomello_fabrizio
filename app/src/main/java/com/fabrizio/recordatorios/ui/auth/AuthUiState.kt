package com.fabrizio.recordatorios.ui.auth

import com.fabrizio.recordatorios.data.auth.AuthError
import com.fabrizio.recordatorios.data.auth.AuthUser

// Estado inmutable de la autenticación. Cada cambio genera una copia nueva (copy).
// La contraseña NUNCA forma parte del estado: solo pasa como parámetro a login/register.
data class AuthUiState(
    // false hasta que Firebase informa por primera vez si hay sesión guardada
    val sesionVerificada: Boolean = false,
    // true mientras hay un login o registro en curso
    val cargando: Boolean = false,
    // null = sin sesión
    val usuario: AuthUser? = null,
    // Último error (de validación local o de Firebase). null = sin error visible.
    val error: AuthError? = null
) {
    val autenticado: Boolean get() = usuario != null
}
