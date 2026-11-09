package com.fabrizio.recordatorios.data.auth

import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

// Tiempo máximo que el splash espera la respuesta del servidor al abrir la app.
// Pasado ese tiempo se sigue con la sesión guardada (por ejemplo, sin red).
const val TIMEOUT_REVALIDACION_MS = 5_000L

// Al abrir la app, Firebase da por buena la sesión guardada en el dispositivo aunque la
// cuenta se haya deshabilitado o borrado. recargar() le pregunta al servidor:
//  - la cuenta ya no es válida (deshabilitada, borrada, token revocado) -> se cierra la sesión;
//  - error de red, timeout o cualquier otra falla -> se mantiene: la app sigue andando offline.
// Devuelve true si cerró la sesión.
suspend fun revalidarSesion(
    recargar: suspend () -> Unit,
    cerrarSesion: () -> Unit,
    timeoutMs: Long = TIMEOUT_REVALIDACION_MS,
    // Parámetro solo para los tests: las excepciones de Firebase no se pueden crear en la JVM
    esErrorDeCuenta: (Throwable) -> Boolean = ::cuentaYaNoEsValida
): Boolean {
    try {
        // null = timeout: se trata igual que un error de red
        withTimeoutOrNull(timeoutMs) { recargar() }
        return false
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        if (!esErrorDeCuenta(e)) return false
        cerrarSesion()
        return true
    }
}

// Solo estos errores dicen algo de la CUENTA. FirebaseNetworkException y el resto no.
fun cuentaYaNoEsValida(e: Throwable): Boolean =
    e is FirebaseAuthInvalidUserException || e is FirebaseAuthInvalidCredentialsException
