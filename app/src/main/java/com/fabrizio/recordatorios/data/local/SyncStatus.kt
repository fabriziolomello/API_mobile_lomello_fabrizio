package com.fabrizio.recordatorios.data.local

// Estado de sincronización de cada fila respecto del servicio remoto.
enum class SyncStatus {
    PENDING_CREATE, // creado localmente, todavía no existe en el servidor
    PENDING_UPDATE, // ya existe en el servidor, tiene cambios locales sin enviar
    PENDING_DELETE, // borrado por el usuario; se oculta en la UI hasta confirmar en el servidor
    SYNCED,         // igual a lo que tiene el servidor
    FAILED          // el servidor rechazó la operación (4xx); espera reintento manual
}
