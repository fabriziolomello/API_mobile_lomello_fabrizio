package com.fabrizio.recordatorios.data.sync

import com.fabrizio.recordatorios.data.remote.DataResult

// Resultado de una vuelta completa de sincronización
sealed interface SyncResult {
    // rechazados: operaciones que el servidor rechazó con 4xx (quedan FAILED)
    data class Exito(val enviados: Int, val recibidos: Int, val rechazados: Int) : SyncResult

    // Ya había una sincronización en curso: no se lanzó otra en paralelo
    data object EnCurso : SyncResult

    // No hay usuario logueado: no se envía ni se baja nada (no hay a quién asignar las filas)
    data object SinSesion : SyncResult

    // Falla recuperable (red, timeout, 5xx): los cambios siguen pendientes en Room
    data class Fallo(val error: DataResult.Error) : SyncResult
}

// Estado observable por la UI
sealed interface EstadoSincronizacion {
    data object Inactivo : EstadoSincronizacion
    data object Sincronizando : EstadoSincronizacion

    // momento: distingue dos errores seguidos con el mismo texto (para volver a mostrar el aviso)
    data class Error(val mensaje: String, val momento: Long) : EstadoSincronizacion
}
