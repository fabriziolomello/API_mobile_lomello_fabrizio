package com.fabrizio.recordatorios.data.cloud

import com.google.firebase.firestore.FirebaseFirestoreException

// Acceso a los recordatorios del usuario en la nube. Interfaz para poder probar la
// sincronización con una implementación en memoria (sin Firebase ni Android).
// Todas las operaciones son por usuario (uid) y lanzan CloudException ante cualquier falla
// (nunca excepciones de Firebase), salvo CancellationException, que se propaga tal cual.
interface CloudReminderDataSource {

    // Crea o reemplaza el documento completo (idempotente: el ID es el clientId)
    suspend fun upsert(uid: String, reminder: CloudReminder)

    // Borrar un documento que no existe no es un error (idempotente)
    suspend fun delete(uid: String, clientId: String)

    // Siempre desde el servidor (nunca desde la caché). Los documentos inválidos se descartan.
    suspend fun fetchAll(uid: String): List<CloudReminder>
}

// Fallas de la nube, ya clasificadas. La sincronización decide qué hacer según el tipo:
//  - SinConexion: temporal, se corta el sync y todo queda pendiente para reintentar.
//  - PermissionDenied / Desconocida: la operación de esa fila se marca como fallida.
//  - NoAutenticado: problema de la sesión, no de la fila.
sealed class CloudException(mensaje: String, causa: Throwable? = null) : Exception(mensaje, causa) {

    abstract val mensajeUsuario: String

    // Sin red, servidor no disponible o sin respuesta dentro del timeout
    class SinConexion(causa: Throwable? = null) : CloudException("Sin conexión con Firestore", causa) {
        override val mensajeUsuario =
            "Sin conexión. Tus cambios quedan guardados y se enviarán al reconectar."
    }

    // Las reglas de seguridad rechazaron la operación
    class PermissionDenied(causa: Throwable? = null) : CloudException("Permiso denegado por Firestore", causa) {
        override val mensajeUsuario =
            "El servidor rechazó el cambio (sin permiso). Tocá Reintentar más tarde."
    }

    // La sesión no es válida para Firestore (token vencido o revocado)
    class NoAutenticado(causa: Throwable? = null) : CloudException("Firestore: usuario no autenticado", causa) {
        override val mensajeUsuario =
            "Tu sesión no es válida para sincronizar. Volvé a iniciar sesión."
    }

    class Desconocida(causa: Throwable) : CloudException("Error de Firestore: ${causa.message}", causa) {
        override val mensajeUsuario = "Ocurrió un error inesperado al sincronizar. Intentá de nuevo."
    }
}

// Mapeo de los códigos de Firestore a CloudException
fun FirebaseFirestoreException.aCloudException(): CloudException = when (code) {
    FirebaseFirestoreException.Code.UNAVAILABLE,
    FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> CloudException.SinConexion(this)
    FirebaseFirestoreException.Code.PERMISSION_DENIED -> CloudException.PermissionDenied(this)
    FirebaseFirestoreException.Code.UNAUTHENTICATED -> CloudException.NoAutenticado(this)
    else -> CloudException.Desconocida(this)
}
