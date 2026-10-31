package com.fabrizio.recordatorios.model

data class Recordatorio(
    val id: Long,
    val titulo: String,
    val categoria: Categoria,
    val prioridad: Prioridad,
    val estado: Estado,
    val descripcion: String? = null,
    // Epoch millis a medianoche UTC (ver FormatoFecha)
    val fechaLimite: Long? = null,
    // Lo calcula la capa de datos; la UI solo lo muestra (no se edita en el formulario)
    val estadoSync: EstadoSync = EstadoSync.SINCRONIZADO
)