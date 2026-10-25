package com.fabrizio.recordatorios.model

sealed class Estado(val etiqueta: String) {
    data object Pendiente : Estado("Pendiente")
    data object Completado : Estado("Completado")
}