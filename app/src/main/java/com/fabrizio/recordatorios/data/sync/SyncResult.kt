package com.fabrizio.recordatorios.data.sync

import com.fabrizio.recordatorios.data.cloud.CloudException

// Resultado de una sincronización completa
sealed interface SyncResult {
    // rechazados: operaciones que la nube rechazó (PermissionDenied / Desconocida)
    data class Exito(val enviados: Int, val recibidos: Int, val rechazados: Int) : SyncResult

    // Ya había una sincronización en curso: no se lanzó otra en paralelo
    data object EnCurso : SyncResult

    // No hay usuario logueado, o la sesión cambió durante el sync: no se escribió nada
    // más en Room (no se mezclan datos entre cuentas)
    data object SinSesion : SyncResult

    // Sin red o sin respuesta a tiempo: se cortó el sync y TODO lo que faltaba sigue
    // pendiente (no se marca FAILED). Se reintenta en el próximo disparador.
    data object SinConexion : SyncResult

    // Falla que impide seguir (pull rechazado, sesión inválida para Firestore).
    // Los datos locales quedan intactos.
    data class Fallo(val error: CloudException) : SyncResult
}

// Estado observable por la UI
sealed interface EstadoSincronizacion {
    data object Inactivo : EstadoSincronizacion
    data object Sincronizando : EstadoSincronizacion

    // momento: distingue dos errores seguidos con el mismo texto (para volver a mostrar el aviso)
    data class Error(val mensaje: String, val momento: Long) : EstadoSincronizacion
}
