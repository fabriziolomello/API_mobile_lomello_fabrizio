package com.fabrizio.recordatorios.data.auth

import com.fabrizio.recordatorios.data.local.ReminderDao
import com.fabrizio.recordatorios.data.preferencias.PreferenciasRepository
import com.fabrizio.recordatorios.data.sync.EstadoSincronizacion
import com.fabrizio.recordatorios.data.sync.SyncManager
import com.fabrizio.recordatorios.data.sync.SyncResult
import kotlinx.coroutines.flow.first

// Cierre de sesión en dos pasos, para que la UI pueda preguntar en el medio:
//   1. sincronizarAntesDeSalir(): intenta subir lo pendiente y cuenta lo que quedó sin subir.
//   2. cerrarSesion(): detiene el sync, borra los datos locales y RECIÉN DESPUÉS hace logout.
// El orden del paso 2 importa: si se hiciera logout primero y la limpieza fallara,
// los datos del usuario quedarían en el dispositivo sin sesión.
class CierreSesion(
    private val dao: ReminderDao,
    private val syncManager: SyncManager,
    // Cancela los sync en curso y deja de escuchar la reconexión (ver AppContainer)
    private val detenerSincronizacion: suspend () -> Unit,
    // Room completo (clearAllTables, fuera del hilo principal)
    private val borrarBaseLocal: suspend () -> Unit,
    private val preferencias: PreferenciasRepository,
    private val auth: AuthRepository
) {

    // Devuelve cuántos recordatorios quedan sin sincronizar (pendientes + rechazados).
    // Sin red, el sync falla rápido y simplemente se cuentan los pendientes.
    suspend fun sincronizarAntesDeSalir(): Int {
        if (syncManager.sincronizar() is SyncResult.EnCurso) {
            // Ya había un sync corriendo (y hará una vuelta extra): se espera a que termine
            esperarSyncEnCurso()
        }
        return contarSinSincronizar()
    }

    suspend fun contarSinSincronizar(): Int = dao.getPendingSync().size + dao.getFailed().size

    suspend fun cerrarSesion() {
        detenerSincronizacion()
        // Un sync manual lanzado desde un ViewModel no vive en el scope de sync:
        // se espera a que termine para que no escriba en Room después de borrarla.
        esperarSyncEnCurso()
        borrarBaseLocal()
        preferencias.borrarDatosDeSesion()
        auth.logout()
    }

    private suspend fun esperarSyncEnCurso() {
        syncManager.estado.first { it != EstadoSincronizacion.Sincronizando }
    }
}
