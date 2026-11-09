package com.fabrizio.recordatorios.data.sync

import com.fabrizio.recordatorios.data.auth.AuthUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

// Reacciona a cada cambio entre "con sesión" y "sin sesión" (no a cada emisión).
// También corre al arrancar sin sesión: si el proceso murió a mitad de una limpieza,
// se completa ahora (borrar una base vacía no hace nada).
// Si la limpieza falla se avisa y se sigue escuchando: un error de disco no debe tirar
// la app ni dejar de seguir la sesión (el próximo cambio a "sin sesión" reintenta).
suspend fun seguirLaSesion(
    usuario: Flow<AuthUser?>,
    alIniciarSesion: () -> Unit,
    alPerderSesion: suspend () -> Unit,
    alFallarLimpieza: (Exception) -> Unit = {}
) {
    usuario
        .map { it != null }
        .distinctUntilChanged()
        .collect { conSesion ->
            if (conSesion) {
                alIniciarSesion()
            } else {
                try {
                    alPerderSesion()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    alFallarLimpieza(e)
                }
            }
        }
}
