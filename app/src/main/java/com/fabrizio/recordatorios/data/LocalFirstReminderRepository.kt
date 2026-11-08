package com.fabrizio.recordatorios.data

import com.fabrizio.recordatorios.data.auth.AuthRepository
import com.fabrizio.recordatorios.data.local.ReminderDao
import com.fabrizio.recordatorios.data.local.ReminderEntity
import com.fabrizio.recordatorios.data.local.SyncStatus
import com.fabrizio.recordatorios.data.mapper.aplicarEdicion
import com.fabrizio.recordatorios.data.mapper.toDomain
import com.fabrizio.recordatorios.data.mapper.toEntity
import com.fabrizio.recordatorios.data.preferencias.PreferenciasRepository
import com.fabrizio.recordatorios.data.sync.EstadoSincronizacion
import com.fabrizio.recordatorios.data.sync.SyncManager
import com.fabrizio.recordatorios.data.sync.SyncResult
import com.fabrizio.recordatorios.model.Estado
import com.fabrizio.recordatorios.model.Recordatorio
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.UUID

// Repositorio local-first: coordina Room (fuente de verdad de la UI), DataStore
// (hora del último sync) y el servidor (a través del SyncManager).
// Toda escritura se guarda primero en Room marcada como pendiente y termina al
// instante; el envío al servidor ocurre después, sin bloquear al usuario.
//
// Aislamiento por usuario: el uid sale de AuthRepository.currentUser.
//  - Lecturas: flatMapLatest sobre la sesión. Si cambia el usuario, la consulta anterior
//    se cancela y se abre otra con el uid nuevo. Sin sesión se emite vacío.
//  - Escrituras: se toma el uid actual; sin sesión fallan con SesionRequeridaException
//    (nunca se guarda una fila con uid vacío).
@OptIn(ExperimentalCoroutinesApi::class) // flatMapLatest
class LocalFirstReminderRepository(
    private val dao: ReminderDao,
    private val syncManager: SyncManager,
    private val preferencias: PreferenciasRepository,
    private val auth: AuthRepository,
    // Inyectables para poder testear con valores fijos
    private val reloj: () -> Long = System::currentTimeMillis,
    private val generarClientId: () -> String = { UUID.randomUUID().toString() }
) : ReminderRepository {

    // uid de la sesión actual (null = sin sesión). Un uid en blanco se trata como sin sesión.
    private val uidSesion: Flow<String?> =
        auth.currentUser
            .map { usuario -> usuario?.uid?.takeIf { it.isNotBlank() } }
            .distinctUntilChanged()

    // Ejecuta la consulta con el uid actual, o emite `sinSesion` si no hay usuario
    private fun <T> porUsuario(sinSesion: T, consulta: (String) -> Flow<T>): Flow<T> =
        uidSesion.flatMapLatest { uid -> if (uid == null) flowOf(sinSesion) else consulta(uid) }

    private suspend fun uidRequerido(): String = uidSesion.first() ?: throw SesionRequeridaException()

    override fun observarRecordatorios(): Flow<List<Recordatorio>> =
        porUsuario(emptyList()) { uid ->
            dao.observeAll(uid).map { entidades -> entidades.map { it.toDomain() } }
        }.distinctUntilChanged()

    override fun observarRecordatorio(id: Long): Flow<Recordatorio?> =
        porUsuario(null) { uid -> dao.observeById(uid, id).map { it?.toDomain() } }
            .distinctUntilChanged()

    override suspend fun crear(recordatorio: Recordatorio): Long {
        val uid = uidRequerido()
        // clientId: clave de idempotencia, generada UNA vez al crear
        val entidad = recordatorio.toEntity(clientId = generarClientId(), userId = uid, ahora = reloj())
        return dao.insert(entidad).also { syncManager.solicitarSync() }
    }

    override suspend fun actualizar(recordatorio: Recordatorio) {
        val actual = buscarVisible(uidRequerido(), recordatorio.id)
        // update (no insert): se modifica la misma fila, nunca se duplica
        dao.update(actual.aplicarEdicion(recordatorio, reloj()))
        syncManager.solicitarSync()
    }

    override suspend fun cambiarCompletado(id: Long, completado: Boolean) {
        val actual = buscarVisible(uidRequerido(), id)
        if (actual.isCompleted == completado) return
        val editado = actual.toDomain().copy(
            estado = if (completado) Estado.Completado else Estado.Pendiente
        )
        dao.update(actual.aplicarEdicion(editado, reloj()))
        syncManager.solicitarSync()
    }

    override suspend fun eliminar(id: Long) {
        val uid = uidRequerido()
        val actual = dao.getById(uid, id) ?: return
        if (actual.syncStatus == SyncStatus.PENDING_DELETE) return
        // SIEMPRE borrado lógico: no se puede saber si el documento ya llegó a la nube
        // (con Firestore no hay remoteId, y un upsert pudo llegar sin confirmarse).
        // La UI lo oculta al instante; la fila se borra de verdad cuando la nube confirme
        // el delete (borrar un documento que no existe también es un éxito).
        dao.update(actual.copy(syncStatus = SyncStatus.PENDING_DELETE, updatedAt = reloj()))
        syncManager.solicitarSync()
    }

    // --- Sincronización ---

    override val estadoSincronizacion: StateFlow<EstadoSincronizacion> = syncManager.estado

    override val cambiosPendientes: Flow<Int> = porUsuario(0) { uid -> dao.observePendingCount(uid) }

    override val ultimaSincronizacion: Flow<Long?> = preferencias.ultimaSincronizacion

    override suspend fun sincronizar(): SyncResult = syncManager.sincronizar()

    override suspend fun reintentar(id: Long) {
        dao.retryFailed(uidRequerido(), id)
        syncManager.sincronizar()
    }

    override suspend fun reintentarFallidos() {
        dao.retryAllFailed(uidRequerido())
        syncManager.sincronizar()
    }

    // Un recordatorio marcado para borrar (o de otro usuario) no se puede editar
    private suspend fun buscarVisible(uid: String, id: Long): ReminderEntity {
        val entidad = dao.getById(uid, id)
        if (entidad == null || entidad.syncStatus == SyncStatus.PENDING_DELETE) {
            throw RecordatorioNoEncontradoException(id)
        }
        return entidad
    }
}
