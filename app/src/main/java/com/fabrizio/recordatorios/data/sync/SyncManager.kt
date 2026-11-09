package com.fabrizio.recordatorios.data.sync

import com.fabrizio.recordatorios.data.cloud.CloudException
import com.fabrizio.recordatorios.data.cloud.CloudFetchResult
import com.fabrizio.recordatorios.data.cloud.CloudReminderDataSource
import com.fabrizio.recordatorios.data.cloud.toCloudReminder
import com.fabrizio.recordatorios.data.cloud.toEntity
import com.fabrizio.recordatorios.data.local.ReminderDao
import com.fabrizio.recordatorios.data.local.ReminderEntity
import com.fabrizio.recordatorios.data.local.SyncStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.atomic.AtomicBoolean

// Sincroniza Room con Firestore (users/{uid}/reminders/{clientId}) en dos pasos:
//   1. PULL: baja todos sus documentos y los mezcla en Room resolviendo conflictos
//      (gana el updatedAt mayor). Así, una versión más nueva de otro dispositivo se
//      aplica ANTES de subir nada y el push no la pisa.
//   2. PUSH: sube lo que quedó pendiente después del pull (lo que ganó lo local).
// Room sigue siendo la fuente de verdad: la UI se entera de todo por los Flow del DAO.
//
// LÍMITES CONOCIDOS de este esquema (sin transacciones en la nube):
//  - Otro dispositivo puede escribir ENTRE el pull y el push: ese cambio no se vio en el
//    pull y el push lo pisa con la versión local (el próximo pull de ese otro dispositivo
//    baja la versión que quedó).
//  - Edición local contra borrado remoto: si el documento se borró en otro dispositivo
//    pero acá hay una edición pendiente, gana la edición: la fila no es SYNCED, así que el
//    pull no la borra, y el push vuelve a crear el documento.
//  - updatedAt sale del reloj de cada dispositivo (ver mezclar).
//
// Aislamiento por usuario: el uid se captura al empezar. Después de CADA espera a la nube
// y antes de escribir en Room se verifica que la sesión siga siendo ese uid; si cambió
// (logout o cambio de cuenta), se aborta sin escribir.
class SyncManager(
    private val dao: ReminderDao,
    private val nube: CloudReminderDataSource,
    private val conectividad: ConnectivityObserver,
    // Scope de la aplicación: un sync no se corta porque el usuario cambie de pantalla
    private val appScope: CoroutineScope,
    // Agrupa las escrituras del PULL en una transacción de Room
    private val enTransaccion: suspend (suspend () -> Unit) -> Unit,
    // Guarda en DataStore la hora del último sync exitoso
    private val registrarSincronizacion: suspend (Long) -> Unit,
    // uid de la sesión actual (null = sin sesión)
    private val usuarioActual: suspend () -> String?,
    private val reloj: () -> Long = System::currentTimeMillis,
    // Avisos de diagnóstico (en la app, Log.w; en los tests se capturan o se ignoran)
    private val log: (String) -> Unit = {},
    // Firestore respondió UNAUTHENTICATED: la sesión ya no sirve. En la app cierra la sesión,
    // y eso dispara la limpieza de datos locales (ver AppContainer.vincularSincronizacionASesion).
    private val alSesionInvalida: () -> Unit = {}
) {

    // Single-flight: un solo sync a la vez. tryLock no espera: si ya hay uno
    // corriendo, el pedido nuevo no lanza otro en paralelo (evita envíos duplicados
    // cuando llegan varios eventos de reconexión seguidos).
    private val mutex = Mutex()

    // Si llega un pedido mientras se sincroniza, se hace una vuelta más al terminar
    // (así no se pierde un cambio guardado durante el sync en curso).
    private val otraVuelta = AtomicBoolean(false)

    private val _estado = MutableStateFlow<EstadoSincronizacion>(EstadoSincronizacion.Inactivo)
    val estado: StateFlow<EstadoSincronizacion> = _estado.asStateFlow()

    // Se llama al iniciar sesión (o al abrir la app con sesión, ver AppContainer):
    // cada vez que hay red, sincroniza. El Flow emite true al suscribirse si hay conexión,
    // así que este es también el PRIMER sync de la sesión.
    fun iniciar() {
        appScope.launch {
            conectividad.estaConectado
                .filter { conectado -> conectado }
                .collect { sincronizar() }
        }
    }

    // Pedido "en segundo plano" después de un cambio local. Sin red no hace nada:
    // el cambio queda pendiente y se envía cuando vuelva la conexión.
    fun solicitarSync() {
        if (!conectividad.hayConexionAhora()) return
        appScope.launch { sincronizar() }
    }

    suspend fun sincronizar(): SyncResult {
        val resultado = sincronizarConLock()
        // Se avisa DESPUÉS de soltar el Mutex y de salir de Sincronizando: el cierre de
        // sesión cancela y espera al sync, así que llamarlo desde adentro lo trabaría.
        if (resultado is SyncResult.Fallo && resultado.error is CloudException.NoAutenticado) {
            log("Firestore rechazó la sesión (UNAUTHENTICATED): se cierra la sesión")
            alSesionInvalida()
        }
        return resultado
    }

    private suspend fun sincronizarConLock(): SyncResult {
        if (!mutex.tryLock()) {
            otraVuelta.set(true)
            return SyncResult.EnCurso
        }
        try {
            _estado.value = EstadoSincronizacion.Sincronizando
            // uid capturado UNA vez: todas las vueltas trabajan para este usuario
            val uid = usuarioActual()?.takeIf { it.isNotBlank() }
            val resultado = if (uid == null) {
                SyncResult.SinSesion
            } else {
                var vuelta: SyncResult
                do {
                    otraVuelta.set(false)
                    vuelta = unaVuelta(uid)
                } while (otraVuelta.get() && vuelta is SyncResult.Exito)
                vuelta
            }
            _estado.value = estadoTras(resultado)
            return resultado
        } finally {
            // Si el sync se canceló a mitad (logout), el estado no puede quedar en
            // Sincronizando: CierreSesion espera a que deje de estarlo antes de borrar Room.
            if (_estado.value == EstadoSincronizacion.Sincronizando) {
                _estado.value = EstadoSincronizacion.Inactivo
            }
            mutex.unlock()
        }
    }

    private fun estadoTras(resultado: SyncResult): EstadoSincronizacion = when (resultado) {
        is SyncResult.Fallo -> EstadoSincronizacion.Error(resultado.error.mensajeUsuario, reloj())
        SyncResult.SinConexion -> EstadoSincronizacion.Error(MENSAJE_SIN_CONEXION, reloj())
        is SyncResult.Exito ->
            if (resultado.rechazados > 0) {
                EstadoSincronizacion.Error(
                    "${resultado.rechazados} cambio(s) fueron rechazados por el servidor. Tocá Reintentar.",
                    reloj()
                )
            } else {
                EstadoSincronizacion.Inactivo
            }
        SyncResult.EnCurso, SyncResult.SinSesion -> EstadoSincronizacion.Inactivo
    }

    // La sesión sigue siendo la del usuario con el que empezó el sync
    private suspend fun sigueLaSesion(uid: String): Boolean = usuarioActual() == uid

    private suspend fun unaVuelta(uid: String): SyncResult {
        // --- 1. PULL ---
        val snapshot: CloudFetchResult = try {
            nube.fetchAll(uid)
        } catch (e: CloudException) {
            // Si el pull falla no se toca nada local y NO se hace el push: sin conocer la
            // nube, subir a ciegas podría pisar una versión más nueva. Todo queda pendiente
            // (sin FAILED) y se reintenta en el próximo sync.
            return if (e is CloudException.SinConexion) SyncResult.SinConexion else SyncResult.Fallo(e)
        }
        if (!sigueLaSesion(uid)) return SyncResult.SinSesion
        val recibidos = mezclar(uid, snapshot)

        // --- 2. PUSH: lo que quedó pendiente después del pull ---
        var enviados = 0
        var rechazados = 0
        for (pendiente in dao.getPendingSync(uid)) {
            val error = enviar(uid, pendiente)
            // Terminó la espera a la nube: antes de tocar Room, la sesión tiene que ser la misma
            if (!sigueLaSesion(uid)) return SyncResult.SinSesion
            when (error) {
                null -> {
                    confirmarEnvio(uid, pendiente)
                    enviados++
                }
                // Sin red: se corta acá. Esta fila y las que faltan quedan como estaban
                // (pendientes, sin FAILED). Reintentar es seguro: el ID del documento es el
                // clientId, así que reenviar pisa el mismo documento y no duplica.
                is CloudException.SinConexion -> return SyncResult.SinConexion
                // La sesión no vale para Firestore: no es culpa de la fila, se corta todo
                is CloudException.NoAutenticado -> return SyncResult.Fallo(error)
                // Rechazo de esta fila: se marca y se sigue con las demás
                is CloudException.PermissionDenied, is CloudException.Desconocida -> {
                    rechazar(uid, pendiente)
                    rechazados++
                }
            }
        }

        registrarSincronizacion(reloj())
        return SyncResult.Exito(enviados, recibidos, rechazados)
    }

    // Envía una operación pendiente. Devuelve null si la nube la confirmó.
    // (CancellationException no es CloudException: se propaga y cancela el sync.)
    private suspend fun enviar(uid: String, entidad: ReminderEntity): CloudException? =
        try {
            when (entidad.syncStatus) {
                SyncStatus.PENDING_CREATE, SyncStatus.PENDING_UPDATE ->
                    nube.upsert(uid, entidad.toCloudReminder())
                SyncStatus.PENDING_DELETE -> nube.delete(uid, entidad.clientId)
                SyncStatus.SYNCED, SyncStatus.FAILED -> Unit // no se reenvían
            }
            null
        } catch (e: CloudException) {
            e
        }

    // Confirma en la MISMA fila; nunca inserta. Comparar updated_at con el valor enviado
    // evita pisar una edición hecha mientras viajaba la petición (queda pendiente).
    private suspend fun confirmarEnvio(uid: String, entidad: ReminderEntity) {
        when (entidad.syncStatus) {
            SyncStatus.PENDING_DELETE -> dao.deleteIfPendingDelete(uid, entidad.id)
            else -> dao.markSynced(uid, entidad.id, entidad.updatedAt)
        }
    }

    private suspend fun rechazar(uid: String, entidad: ReminderEntity) {
        when (entidad.syncStatus) {
            // Un borrado rechazado NO se marca FAILED: FAILED es visible en la lista y el
            // reintento lo convertiría en upsert, resucitando el recordatorio. Queda
            // PENDING_DELETE (oculto) y se vuelve a intentar en el próximo sync.
            SyncStatus.PENDING_DELETE -> Unit
            // Lógica de reintento existente: FAILED hasta que el usuario toque Reintentar
            // (salvo que lo haya vuelto a editar mientras tanto)
            else -> dao.markFailed(uid, entidad.id, entidad.updatedAt)
        }
    }

    // Mezcla los documentos de la nube en Room. Devuelve cuántas filas se insertaron,
    // cambiaron o se borraron. Siempre se busca por client_id antes de insertar:
    // nunca se inserta a ciegas, así que no puede haber duplicados.
    //
    // CONFLICTOS: gana el updatedAt mayor. LÍMITE CONOCIDO: updatedAt sale del reloj de
    // cada dispositivo; si un reloj está adelantado o atrasado, sus cambios ganan o pierden
    // de más. Ante empate gana lo local (se sube en el push que sigue).
    private suspend fun mezclar(uid: String, snapshot: CloudFetchResult): Int {
        var cambios = 0
        enTransaccion {
            val enLaNube = HashSet<String>(snapshot.documentos.size)
            for (doc in snapshot.documentos) {
                enLaNube += doc.clientId
                val local = dao.getByClientId(uid, doc.clientId)
                when {
                    // No existe local: se inserta como SYNCED
                    local == null -> {
                        dao.insert(doc.toEntity(uid))
                        cambios++
                    }
                    // Gana el borrado local: no se resucita (el push lo borrará en la nube)
                    local.syncStatus == SyncStatus.PENDING_DELETE -> Unit
                    // SYNCED, PENDING_CREATE/UPDATE o FAILED con una versión más nueva en la
                    // nube: se pisa con la de la nube y queda SYNCED (misma fila, mismo id)
                    doc.updatedAt > local.updatedAt -> {
                        dao.update(doc.toEntity(uid).copy(id = local.id))
                        cambios++
                    }
                    // Lo local es igual o más nuevo: si estaba pendiente sigue pendiente
                    // y se sube en el push que viene a continuación
                    else -> Unit
                }
            }
            // Filas SYNCED que ya no están en la nube: se borraron desde otro dispositivo.
            // Solo SYNCED: nunca se borran pendientes ni FAILED (tienen cambios sin subir).
            // Un documento que EXISTE pero el mapper descartó (inválido) no es un borrado:
            // su fila local se deja como está.
            for (fila in dao.getSynced(uid)) {
                when (fila.clientId) {
                    in enLaNube -> Unit
                    in snapshot.idsInvalidos ->
                        log("Documento ${fila.clientId} inválido en la nube: se conserva la fila local")
                    else -> {
                        dao.deleteById(uid, fila.id)
                        cambios++
                    }
                }
            }
        }
        return cambios
    }

    private companion object {
        const val MENSAJE_SIN_CONEXION =
            "Sin conexión. Tus cambios quedan guardados y se enviarán al reconectar."
    }
}
