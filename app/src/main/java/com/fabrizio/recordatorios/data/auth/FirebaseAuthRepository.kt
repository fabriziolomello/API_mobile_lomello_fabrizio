package com.fabrizio.recordatorios.data.auth

import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await

// Implementación con Firebase Auth. Es el único lugar de la app que conoce FirebaseUser.
// Nunca se loguean contraseñas (ni acá ni en ninguna otra capa).
class FirebaseAuthRepository(
    private val auth: FirebaseAuth
) : AuthRepository {

    // callbackFlow convierte el listener de Firebase en un Flow. Firebase llama al listener
    // apenas se registra (estado inicial) y luego en cada cambio de sesión.
    override val currentUser: Flow<AuthUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser?.aAuthUser())
        }
        auth.addAuthStateListener(listener)
        // Al cancelarse la colección se quita el listener (evita fugas)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    override suspend fun register(email: String, password: String): Result<AuthUser> =
        autenticar { auth.createUserWithEmailAndPassword(email, password).await() }

    override suspend fun login(email: String, password: String): Result<AuthUser> =
        autenticar { auth.signInWithEmailAndPassword(email, password).await() }

    override fun logout() {
        auth.signOut()
    }

    override suspend fun revalidarSesion() {
        val usuario = auth.currentUser ?: return
        revalidarSesion(recargar = { usuario.reload().await() }, cerrarSesion = ::logout)
    }

    // Ejecuta la operación y traduce cualquier falla a AuthError
    private suspend fun autenticar(operacion: suspend () -> AuthResult): Result<AuthUser> =
        try {
            val usuario = operacion().user
                ?: return Result.failure(AuthError.Desconocido(null))
            Result.success(usuario.aAuthUser())
        } catch (e: CancellationException) {
            throw e // cancelar la corrutina no es un error de autenticación
        } catch (e: Exception) {
            Result.failure(mapearErrorAuth(e))
        }
}

private fun FirebaseUser.aAuthUser() = AuthUser(uid = uid, email = email)
