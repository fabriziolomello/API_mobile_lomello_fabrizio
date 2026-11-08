package com.fabrizio.recordatorios.data.cloud

import android.util.Log
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

// Implementación con Firestore. Ruta: users/{uid}/reminders/{clientId}.
// Sin snapshot listeners: la sincronización pide los datos cuando la necesita (fetchAll).
//
// COMPORTAMIENTO SIN CONEXIÓN (caché persistente desactivada, ver crearFirestoreSinCachePersistente):
//  - set() y delete(): el Task se completa recién cuando el SERVIDOR confirma la escritura.
//    Sin red NO falla: la escritura queda en la cola en memoria del cliente de Firestore
//    esperando la reconexión (solo mientras viva el proceso, porque la caché no va a disco).
//    Por eso cada operación tiene un timeout, que se traduce a SinConexion.
//  - Cancelar el await (timeout o cancelación del sync) NO cancela la escritura encolada:
//    puede llegar al servidor más tarde. No es un problema porque set() escribe el documento
//    completo con ID = clientId (idempotente) y la cola respeta el orden: el próximo sync
//    vuelve a enviar el mismo contenido o uno más nuevo, encolado detrás.
//  - get(Source.SERVER): nunca lee de la caché. Sin red falla con UNAVAILABLE cuando el
//    cliente detecta que está offline (puede tardar unos segundos); el timeout cubre el
//    caso en que se quede esperando.
class FirestoreReminderDataSource(
    private val firestore: FirebaseFirestore
) : CloudReminderDataSource {

    private fun coleccion(uid: String): CollectionReference {
        require(uid.isNotBlank()) { "uid vacío: no se accede a Firestore sin usuario" }
        return firestore.collection(COLECCION_USUARIOS).document(uid).collection(COLECCION_RECORDATORIOS)
    }

    override suspend fun upsert(uid: String, reminder: CloudReminder) {
        conTimeout { coleccion(uid).document(reminder.clientId).set(reminder.toMap()).await() }
    }

    override suspend fun delete(uid: String, clientId: String) {
        conTimeout { coleccion(uid).document(clientId).delete().await() }
    }

    override suspend fun fetchAll(uid: String): CloudFetchResult = conTimeout {
        val validos = mutableListOf<CloudReminder>()
        val invalidos = mutableSetOf<String>()
        for (documento in coleccion(uid).get(Source.SERVER).await().documents) {
            val reminder = cloudReminderDesdeDocumento(documento.id, documento.data.orEmpty()) { motivo ->
                Log.w(TAG, motivo)
            }
            if (reminder != null) validos += reminder else invalidos += documento.id
        }
        CloudFetchResult(validos, invalidos)
    }

    // Aplica el timeout y traduce toda falla a CloudException.
    // El orden de los catch importa: TimeoutCancellationException ES una
    // CancellationException, así que va primero. Cualquier otra cancelación (por ejemplo,
    // el logout cancelando el sync) se propaga tal cual: nunca se traga.
    private suspend fun <T> conTimeout(operacion: suspend () -> T): T =
        try {
            withTimeout(TIMEOUT_OPERACION_MS) { operacion() }
        } catch (e: TimeoutCancellationException) {
            throw CloudException.SinConexion(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            throw e.aCloudException()
        } catch (e: CloudException) {
            throw e
        } catch (e: Exception) {
            throw CloudException.Desconocida(e)
        }

    companion object {
        private const val TAG = "FirestoreReminders"
        const val COLECCION_USUARIOS = "users"
        const val COLECCION_RECORDATORIOS = "reminders"

        // Tiempo máximo de espera de cada operación (sin red, el Task no falla solo)
        const val TIMEOUT_OPERACION_MS = 15_000L
    }
}

// Instancia de Firestore con la caché persistente DESACTIVADA (solo caché en memoria).
// Room es la única fuente de verdad offline; una segunda copia en disco duplicaría datos
// del usuario y podría sobrevivir al logout.
// API vigente en firebase-firestore 26.6.0 (BoM 34.19.0): setLocalCacheSettings con
// MemoryCacheSettings (setPersistenceEnabled está deprecado).
// Debe llamarse ANTES de cualquier otro uso de la instancia: si ya se usó,
// setFirestoreSettings lanza IllegalStateException. En la app, AppContainer es el único
// lugar que obtiene la instancia y lo hace a través de esta función.
fun crearFirestoreSinCachePersistente(): FirebaseFirestore =
    FirebaseFirestore.getInstance().apply {
        firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
            .build()
    }
