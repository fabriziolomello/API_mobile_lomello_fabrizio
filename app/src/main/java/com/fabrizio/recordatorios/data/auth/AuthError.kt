package com.fabrizio.recordatorios.data.auth

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException

// Errores de autenticación que entiende la app. Las excepciones de Firebase se traducen ACÁ,
// en la capa de datos (igual que DataResult con las de red): el ViewModel nunca las ve.
// Extiende Exception para poder viajar dentro de Result.failure.
sealed class AuthError(val mensajeUsuario: String, causa: Throwable? = null) : Exception(mensajeUsuario, causa) {

    data object CorreoInvalido : AuthError("El correo no tiene un formato válido.")

    data object ContrasenaDebil :
        AuthError("La contraseña es muy débil: usá al menos ${ValidacionAuth.CONTRASENA_MIN_CARACTERES} caracteres.")

    data object CorreoYaRegistrado : AuthError("Ya existe una cuenta con ese correo.")

    // Correo o contraseña incorrectos. No se distingue cuál de los dos falló
    // para no revelar qué correos tienen cuenta.
    data object CredencialesIncorrectas : AuthError("Correo o contraseña incorrectos.")

    data object SinRed : AuthError("Sin conexión. Revisá tu red e intentá de nuevo.")

    class Desconocido(causa: Throwable?) : AuthError("Ocurrió un error inesperado. Intentá de nuevo.", causa)
}

// Traduce cualquier excepción de Firebase Auth a un AuthError.
// El orden importa: FirebaseAuthWeakPasswordException es una FirebaseAuthInvalidCredentialsException.
fun mapearErrorAuth(e: Throwable): AuthError = when (e) {
    is AuthError -> e
    is FirebaseNetworkException -> AuthError.SinRed
    is FirebaseAuthWeakPasswordException -> AuthError.ContrasenaDebil
    is FirebaseAuthUserCollisionException -> AuthError.CorreoYaRegistrado
    is FirebaseAuthException -> when (e.errorCode) {
        "ERROR_INVALID_EMAIL" -> AuthError.CorreoInvalido
        "ERROR_WEAK_PASSWORD" -> AuthError.ContrasenaDebil
        "ERROR_EMAIL_ALREADY_IN_USE" -> AuthError.CorreoYaRegistrado
        // Con la protección contra enumeración de correos, Firebase devuelve
        // ERROR_INVALID_CREDENTIAL tanto para contraseña mala como para usuario inexistente
        "ERROR_INVALID_CREDENTIAL", "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND" ->
            AuthError.CredencialesIncorrectas
        else -> when (e) {
            is FirebaseAuthInvalidCredentialsException,
            is FirebaseAuthInvalidUserException -> AuthError.CredencialesIncorrectas
            else -> AuthError.Desconocido(e)
        }
    }
    else -> AuthError.Desconocido(e)
}
