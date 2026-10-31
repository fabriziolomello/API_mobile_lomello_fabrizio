package com.fabrizio.recordatorios.data.remote

import kotlinx.serialization.Serializable

// Contrato JSON del recurso "reminders" en el servidor (MockAPI).
// Solo lo conocen Retrofit y los mappers: nunca llega a Room ni a la UI.
// Los valores por defecto toleran registros incompletos (por ejemplo, creados a mano en el panel).
@Serializable
data class ReminderDto(
    // Lo asigna el servidor; null en un POST
    val id: String? = null,
    // Clave de idempotencia generada en el dispositivo
    val clientId: String? = null,
    val title: String = "",
    val description: String? = null,
    val category: String? = null,
    val priority: String? = null,
    val completed: Boolean = false,
    val dueDate: Long? = null,
    val createdAt: Long? = null,
    val updatedAt: Long? = null
)
