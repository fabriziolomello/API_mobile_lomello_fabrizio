package com.fabrizio.recordatorios

import com.fabrizio.recordatorios.data.auth.AuthError
import com.fabrizio.recordatorios.data.auth.AuthRepository
import com.fabrizio.recordatorios.data.auth.AuthUser
import com.fabrizio.recordatorios.data.local.ReminderDao
import com.fabrizio.recordatorios.data.local.ReminderEntity
import com.fabrizio.recordatorios.data.local.SyncStatus
import com.fabrizio.recordatorios.data.preferencias.CriterioOrden
import com.fabrizio.recordatorios.data.preferencias.FiltroPrioridad
import com.fabrizio.recordatorios.data.preferencias.PreferenciasRepository
import com.fabrizio.recordatorios.data.preferencias.PreferenciasUsuario
import com.fabrizio.recordatorios.data.preferencias.TemaApp
import com.fabrizio.recordatorios.data.remote.DataResult
import com.fabrizio.recordatorios.data.remote.ReminderDto
import com.fabrizio.recordatorios.data.remote.ReminderRemoteDataSource
import com.fabrizio.recordatorios.data.sync.ConnectivityObserver
import com.fabrizio.recordatorios.model.Prioridad
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

private val PENDIENTES = setOf(SyncStatus.PENDING_CREATE, SyncStatus.PENDING_UPDATE, SyncStatus.PENDING_DELETE)

// DAO en memoria que imita las consultas de ReminderDao (sin Room ni Android)
class FakeReminderDao : ReminderDao {
    val filas = MutableStateFlow<List<ReminderEntity>>(emptyList())
    private var proximoId = 1L

    private fun visibles() = filas.map { lista -> lista.filter { it.syncStatus != SyncStatus.PENDING_DELETE } }
    private fun modificar(id: Long, cambio: (ReminderEntity) -> ReminderEntity?): Int {
        var cambiadas = 0
        filas.value = filas.value.mapNotNull { if (it.id == id) { cambiadas++; cambio(it) } else it }
        return cambiadas
    }

    override suspend fun insert(reminder: ReminderEntity): Long {
        require(filas.value.none { it.clientId == reminder.clientId }) { "clientId duplicado" }
        val id = proximoId++
        filas.value = filas.value + reminder.copy(id = id)
        return id
    }

    override suspend fun update(reminder: ReminderEntity) {
        modificar(reminder.id) { reminder }
    }

    override suspend fun delete(reminder: ReminderEntity) {
        filas.value = filas.value.filterNot { it.id == reminder.id }
    }

    override fun observeAll(): Flow<List<ReminderEntity>> = visibles()
    override fun observeById(id: Long): Flow<ReminderEntity?> = visibles().map { l -> l.find { it.id == id } }
    override fun observeByCompleted(completed: Boolean) = visibles().map { l -> l.filter { it.isCompleted == completed } }
    override fun observeByPriority(priority: Prioridad) = visibles().map { l -> l.filter { it.priority == priority } }
    override suspend fun getById(id: Long) = filas.value.find { it.id == id }
    override suspend fun getByClientId(clientId: String) = filas.value.find { it.clientId == clientId }
    override suspend fun getByRemoteId(remoteId: String) = filas.value.find { it.remoteId == remoteId }
    override suspend fun getPendingSync() = filas.value.filter { it.syncStatus in PENDIENTES }.sortedBy { it.updatedAt }
    override suspend fun getFailed() = filas.value.filter { it.syncStatus == SyncStatus.FAILED }
    override suspend fun getWithRemoteId() = filas.value.filter { it.remoteId != null }
    override fun observePendingCount() = filas.map { l -> l.count { it.syncStatus in PENDIENTES } }

    override suspend fun markSynced(id: Long, remoteId: String, updatedAtEnviado: Long): Int {
        if (getById(id)?.updatedAt != updatedAtEnviado) return 0
        return modificar(id) { it.copy(remoteId = remoteId, syncStatus = SyncStatus.SYNCED) }
    }

    override suspend fun assignRemoteId(id: Long, remoteId: String) = modificar(id) {
        it.copy(
            remoteId = remoteId,
            syncStatus = if (it.syncStatus == SyncStatus.PENDING_CREATE) SyncStatus.PENDING_UPDATE else it.syncStatus
        )
    }

    override suspend fun markFailed(id: Long, updatedAtEnviado: Long): Int {
        if (getById(id)?.updatedAt != updatedAtEnviado) return 0
        return modificar(id) { it.copy(syncStatus = SyncStatus.FAILED) }
    }

    override suspend fun resetToPendingCreate(id: Long) {
        modificar(id) { it.copy(remoteId = null, syncStatus = SyncStatus.PENDING_CREATE) }
    }

    override suspend fun deleteIfPendingDelete(id: Long): Int {
        if (getById(id)?.syncStatus != SyncStatus.PENDING_DELETE) return 0
        return modificar(id) { null }
    }

    override suspend fun retryFailed(id: Long): Int {
        if (getById(id)?.syncStatus != SyncStatus.FAILED) return 0
        return modificar(id) {
            it.copy(syncStatus = if (it.remoteId == null) SyncStatus.PENDING_CREATE else SyncStatus.PENDING_UPDATE)
        }
    }

    override suspend fun retryAllFailed(): Int = getFailed().sumOf { retryFailed(it.id) }
}

// Servidor falso en memoria, con fallas configurables
class FakeRemoteDataSource : ReminderRemoteDataSource {
    val servidor = mutableListOf<ReminderDto>()
    private var proximoId = 1
    var posts = 0
        private set

    // Si no es null, todas las llamadas fallan con este error
    var falla: DataResult.Error? = null

    // El POST se guarda en el servidor pero la app recibe un timeout
    var perderRespuestaDelPost = false

    // Permite "frenar" un POST a mitad de camino (prueba del Mutex)
    var bloqueoPost: CompletableDeferred<Unit>? = null

    override suspend fun obtenerTodos(): DataResult<List<ReminderDto>> =
        falla ?: DataResult.Exito(servidor.toList())

    override suspend fun buscarPorClientId(clientId: String): DataResult<ReminderDto?> =
        falla ?: DataResult.Exito(servidor.find { it.clientId == clientId })

    override suspend fun crear(dto: ReminderDto): DataResult<ReminderDto> {
        falla?.let { return it }
        bloqueoPost?.await()
        posts++
        val nuevo = dto.copy(id = (proximoId++).toString())
        servidor += nuevo
        return if (perderRespuestaDelPost) DataResult.Timeout else DataResult.Exito(nuevo)
    }

    override suspend fun actualizar(remoteId: String, dto: ReminderDto): DataResult<ReminderDto> {
        falla?.let { return it }
        val indice = servidor.indexOfFirst { it.id == remoteId }
        if (indice < 0) return DataResult.ErrorCliente(404)
        servidor[indice] = dto.copy(id = remoteId)
        return DataResult.Exito(servidor[indice])
    }

    override suspend fun eliminar(remoteId: String): DataResult<Unit> {
        falla?.let { return it }
        servidor.removeAll { it.id == remoteId }
        return DataResult.Exito(Unit)
    }
}

class FakeConnectivityObserver(var conectado: Boolean = false) : ConnectivityObserver {
    override val estaConectado: Flow<Boolean> get() = flowOf(conectado)
    override fun hayConexionAhora() = conectado
}

class FakePreferenciasRepository : PreferenciasRepository {
    val ultima = MutableStateFlow<Long?>(null)
    override val preferencias: Flow<PreferenciasUsuario> = flowOf(PreferenciasUsuario())
    override val ultimaSincronizacion: Flow<Long?> = ultima
    override suspend fun guardarUltimaSincronizacion(momento: Long) { ultima.value = momento }
    var datosDeSesionBorrados = false
        private set
    override suspend fun borrarDatosDeSesion() {
        ultima.value = null
        datosDeSesionBorrados = true
    }
    override suspend fun guardarTema(tema: TemaApp) = Unit
    override suspend fun guardarMostrarCompletados(mostrar: Boolean) = Unit
    override suspend fun guardarOrden(orden: CriterioOrden) = Unit
    override suspend fun guardarFiltroPrioridad(filtro: FiltroPrioridad) = Unit
    override suspend fun guardarTemaInvalidoParaPrueba() = Unit
}

// Autenticación falsa en memoria: registra las llamadas y permite forzar un error
class FakeAuthRepository(usuarioInicial: AuthUser? = null) : AuthRepository {
    val sesion = MutableStateFlow(usuarioInicial)
    override val currentUser: Flow<AuthUser?> = sesion

    // Si no es null, register y login fallan con este error
    var falla: AuthError? = null
    var llamadas = 0
        private set
    var ultimoCorreo: String? = null
        private set

    private fun autenticar(email: String): Result<AuthUser> {
        llamadas++
        ultimoCorreo = email
        falla?.let { return Result.failure(it) }
        val usuario = AuthUser(uid = "uid-1", email = email)
        sesion.value = usuario
        return Result.success(usuario)
    }

    override suspend fun register(email: String, password: String) = autenticar(email)
    override suspend fun login(email: String, password: String) = autenticar(email)
    // Permite verificar en qué momento se llamó a logout()
    var alCerrarSesion: () -> Unit = {}
    override fun logout() {
        alCerrarSesion()
        sesion.value = null
    }
}
