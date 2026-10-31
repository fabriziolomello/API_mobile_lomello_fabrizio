package com.fabrizio.recordatorios.model

// Estado de sincronización tal como lo ve el usuario (simplifica los 5 SyncStatus de Room)
enum class EstadoSync(val etiqueta: String) {
    PENDIENTE("Pendiente de sincronizar"),
    SINCRONIZADO("Sincronizado"),
    ERROR("Error al sincronizar")
}
