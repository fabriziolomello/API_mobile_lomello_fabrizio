package com.fabrizio.recordatorios.data.auth

import kotlinx.coroutines.flow.Flow

// Contrato de autenticación. El resto de la app depende de esta interfaz,
// no de Firebase (así el ViewModel se testea con un repositorio falso).
interface AuthRepository {

    // Usuario actual: emite al suscribirse y ante cada inicio o cierre de sesión. null = sin sesión.
    val currentUser: Flow<AuthUser?>

    // En caso de error, el Result.failure contiene siempre un AuthError
    suspend fun register(email: String, password: String): Result<AuthUser>

    suspend fun login(email: String, password: String): Result<AuthUser>

    fun logout()
}
