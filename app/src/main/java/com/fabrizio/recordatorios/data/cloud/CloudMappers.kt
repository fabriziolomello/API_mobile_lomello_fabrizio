package com.fabrizio.recordatorios.data.cloud

import com.fabrizio.recordatorios.data.local.ReminderEntity
import com.fabrizio.recordatorios.data.local.SyncStatus
import com.fabrizio.recordatorios.model.Categoria
import com.fabrizio.recordatorios.model.Prioridad

// Mappers escritos a mano (sin toObject() ni reflexión): con R8 activo en release,
// las clases mapeadas por reflexión necesitan reglas keep y fallan en silencio si faltan.

// Nombres de los campos del documento. Son el contrato con Firestore: no renombrar.
internal object CamposCloud {
    const val CLIENT_ID = "clientId"
    const val TITLE = "title"
    const val DESCRIPTION = "description"
    const val CATEGORY = "category"
    const val PRIORITY = "priority"
    const val COMPLETED = "completed"
    const val DUE_DATE = "dueDate"
    const val CREATED_AT = "createdAt"
    const val UPDATED_AT = "updatedAt"
}

// --- CloudReminder <-> Map (documento de Firestore) ---

// Documento completo. clientId se guarda también como campo (útil al mirar la consola),
// pero al leer manda el ID del documento.
fun CloudReminder.toMap(): Map<String, Any?> = mapOf(
    CamposCloud.CLIENT_ID to clientId,
    CamposCloud.TITLE to title,
    CamposCloud.DESCRIPTION to description,
    CamposCloud.CATEGORY to category.codigo,
    CamposCloud.PRIORITY to priority.codigo,
    CamposCloud.COMPLETED to completed,
    CamposCloud.DUE_DATE to dueDate,
    CamposCloud.CREATED_AT to createdAt,
    CamposCloud.UPDATED_AT to updatedAt
)

// Lectura defensiva de un documento. Devuelve null (y avisa con alDescartar) si falta un
// campo obligatorio o algún campo tiene un tipo incorrecto: un documento roto se descarta,
// nunca crashea la sincronización.
// - Los números se leen como Number: Firestore puede devolver Long o Double.
// - El ID del documento manda sobre el campo clientId del contenido.
// - Un código de categoría/prioridad desconocido no es un error: toma un valor seguro.
fun cloudReminderDesdeDocumento(
    documentId: String,
    datos: Map<String, Any?>,
    alDescartar: (motivo: String) -> Unit
): CloudReminder? {
    if (documentId.isBlank()) {
        alDescartar("documento sin ID")
        return null
    }
    val lector = LectorCampos(datos)
    val reminder = CloudReminder(
        clientId = documentId,
        title = lector.obligatorio<String>(CamposCloud.TITLE),
        description = lector.opcional<String>(CamposCloud.DESCRIPTION),
        category = Categoria.desdeCodigo(lector.obligatorio<String>(CamposCloud.CATEGORY)),
        priority = Prioridad.desdeCodigo(lector.obligatorio<String>(CamposCloud.PRIORITY)),
        completed = lector.obligatorio<Boolean>(CamposCloud.COMPLETED),
        dueDate = lector.opcional<Number>(CamposCloud.DUE_DATE)?.toLong(),
        createdAt = lector.obligatorio<Number>(CamposCloud.CREATED_AT).toLong(),
        updatedAt = lector.obligatorio<Number>(CamposCloud.UPDATED_AT).toLong()
    )
    lector.error?.let { motivo ->
        alDescartar("documento $documentId descartado: $motivo")
        return null
    }
    return reminder
}

// Junta el PRIMER error en vez de cortar con una excepción. Mientras haya error devuelve
// valores de relleno que nunca se usan (el resultado se descarta si error != null).
private class LectorCampos(private val datos: Map<String, Any?>) {
    var error: String? = null
        private set

    fun registrar(motivo: String) {
        if (error == null) error = motivo
    }

    inline fun <reified T : Any> obligatorio(campo: String): T =
        when (val valor = datos[campo]) {
            null -> relleno("falta el campo obligatorio '$campo'")
            is T -> valor
            else -> relleno("'$campo' tiene tipo ${valor::class.simpleName}, se esperaba ${T::class.simpleName}")
        }

    inline fun <reified T : Any> opcional(campo: String): T? =
        when (val valor = datos[campo]) {
            null -> null
            is T -> valor
            else -> {
                registrar("'$campo' tiene tipo ${valor::class.simpleName}, se esperaba ${T::class.simpleName}")
                null
            }
        }

    // Valor de relleno para seguir leyendo; el resultado se descarta porque error != null
    inline fun <reified T : Any> relleno(motivo: String): T {
        registrar(motivo)
        val valor: Any = when (T::class) {
            String::class -> ""
            Boolean::class -> false
            Number::class -> 0L
            else -> throw IllegalArgumentException("Tipo de campo no soportado: ${T::class.simpleName}")
        }
        return valor as T
    }
}

// --- CloudReminder <-> ReminderEntity ---

// Lo que se sube: solo contenido + clientId + updatedAt (el uid va en la ruta)
fun ReminderEntity.toCloudReminder(): CloudReminder = CloudReminder(
    clientId = clientId,
    title = title,
    description = description,
    category = category,
    priority = priority,
    completed = isCompleted,
    dueDate = dueDate,
    createdAt = createdAt,
    updatedAt = updatedAt
)

// Lo que se baja: queda SYNCED (igual a la nube) y sin remoteId (Firestore usa clientId).
// id = 0 para insertar; para pisar una fila existente usar .copy(id = local.id).
fun CloudReminder.toEntity(userId: String): ReminderEntity = ReminderEntity(
    clientId = clientId,
    userId = userId,
    remoteId = null,
    title = title,
    description = description,
    category = category,
    priority = priority,
    isCompleted = completed,
    dueDate = dueDate,
    createdAt = createdAt,
    updatedAt = updatedAt,
    syncStatus = SyncStatus.SYNCED
)
