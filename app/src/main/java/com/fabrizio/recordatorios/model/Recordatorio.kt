package com.fabrizio.recordatorios.model

data class Recordatorio(
    val id: Long,
    val titulo: String,
    val categoria: Categoria,
    val prioridad: Prioridad,
    val estado: Estado,
    val descripcion: String? = null,
    val fechaLimite: String? = null
)