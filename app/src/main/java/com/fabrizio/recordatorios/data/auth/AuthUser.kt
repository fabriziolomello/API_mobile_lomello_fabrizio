package com.fabrizio.recordatorios.data.auth

// Usuario autenticado, propio de la app. La UI y los ViewModel solo conocen este modelo:
// FirebaseUser queda encerrado en FirebaseAuthRepository.
data class AuthUser(
    val uid: String,
    val email: String?
)
