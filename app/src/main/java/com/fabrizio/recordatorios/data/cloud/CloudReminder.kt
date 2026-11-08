package com.fabrizio.recordatorios.data.cloud

import com.fabrizio.recordatorios.model.Categoria
import com.fabrizio.recordatorios.model.Prioridad

// Recordatorio tal como se guarda en Firestore (users/{uid}/reminders/{clientId}).
// - Sin userId: el dueño va en la ruta del documento.
// - Sin campos de sincronización local (id de Room, syncStatus, remoteId).
// - clientId es también el ID del documento: subir dos veces lo mismo pisa el mismo
//   documento (idempotente), nunca crea un duplicado.
data class CloudReminder(
    val clientId: String,
    val title: String,
    val description: String?,
    val category: Categoria,
    val priority: Prioridad,
    val completed: Boolean,
    // Epoch millis (medianoche UTC, ver FormatoFecha)
    val dueDate: Long?,
    val createdAt: Long,
    // Epoch millis según el reloj del DISPOSITIVO que hizo el último cambio.
    // Se usa para resolver conflictos (gana el mayor).
    val updatedAt: Long
)
