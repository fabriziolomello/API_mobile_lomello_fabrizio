package com.fabrizio.recordatorios

import com.fabrizio.recordatorios.data.auth.AuthError
import com.fabrizio.recordatorios.data.auth.AuthRepository
import com.fabrizio.recordatorios.data.auth.AuthUser
import com.fabrizio.recordatorios.data.cloud.CloudException
import com.fabrizio.recordatorios.data.cloud.CloudReminder
import com.fabrizio.recordatorios.data.cloud.CloudReminderDataSource
import com.fabrizio.recordatorios.data.local.ReminderDao
import com.fabrizio.recordatorios.data.local.ReminderEntity
import com.fabrizio.recordatorios.data.local.SyncStatus
import com.fabrizio.recordatorios.data.preferencias.CriterioOrden
import com.fabrizio.recordatorios.data.preferencias.FiltroPrioridad
import com.fabrizio.recordatorios.data.preferencias.PreferenciasRepository
import com.fabrizio.recordatorios.data.preferencias.PreferenciasUsuario
import com.fabrizio.recordatorios.data.preferencias.TemaApp
import com.fabrizio.recordatorios.data.sync.ConnectivityObserver
import com.fabrizio.recordatorios.model.Prioridad
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

private val PENDIENTES = setOf(SyncStatus.PENDING_CREATE, SyncStatus.PENDING_UPDATE, SyncStatus.PENDING_DELETE)

// DAO en memoria que imita las consultas de ReminderDao (sin Room ni Android).
// Igual que el real, toda consulta filtra por userId.
class FakeReminderDao : ReminderDao {
    val filas = MutableStateFlow<List<ReminderEntity>>(emptyList())
    private var proximoId = 1L

    private fun delUsuario(userId: String) = filas.value.filter { it.userId == userId }
    private fun visibles(userId: String) =
        filas.map { lista -> lista.filter { it.userId == userId && it.syncStatus != SyncStatus.PENDING_DELETE } }
    private fun modificar(userId: String, id: Long, cambio: (ReminderEntity) -> ReminderEntity?): Int {
        var cambiadas = 0
        filas.value = filas.value.mapNotNull {
            if (it.id == id && it.userId == userId) { cambiadas++; cambio(it) } else it
        }
        return cambiadas
    }

    override suspend fun insert(reminder: ReminderEntity): Long {
        // client_id es único en toda la tabla (como el índice UNIQUE de Room)
        require(filas.value.none { it.clientId == reminder.clientId }) { "clientId duplicado" }
        val id = proximoId++
        filas.value = filas.value + reminder.copy(id = id)
        return id
    }

    override suspend fun update(reminder: ReminderEntity) {
        filas.value = filas.value.map { if (it.id == reminder.id) reminder else it }
    }

    override suspend fun deleteById(userId: String, id: Long) = modificar(userId, id) { null }

    override fun observeAll(userId: String): Flow<List<ReminderEntity>> = visibles(userId)
    override fun observeById(userId: String, id: Long): Flow<ReminderEntity?> =
        visibles(userId).map { l -> l.find { it.id == id } }
    override fun observeByCompleted(userId: String, completed: Boolean) =
        visibles(userId).map { l -> l.filter { it.isCompleted == completed } }
    override fun observeByPriority(userId: String, priority: Prioridad) =
        visibles(userId).map { l -> l.filter { it.priority == priority } }
    override suspend fun getById(userId: String, id: Long) = delUsuario(userId).find { it.id == id }
    override suspend fun getByClientId(userId: String, clientId: String) =
        delUsuario(userId).find { it.clientId == clientId }
    override suspend fun getPendingSync(userId: String) =
        delUsuario(userId).filter { it.syncStatus in PENDIENTES }.sortedBy { it.updatedAt }
    override suspend fun getFailed(userId: String) = delUsuario(userId).filter { it.syncStatus == SyncStatus.FAILED }
    override suspend fun getSynced(userId: String) = delUsuario(userId).filter { it.syncStatus == SyncStatus.SYNCED }
    override fun observePendingCount(userId: String) =
        filas.map { l -> l.count { it.userId == userId && it.syncStatus in PENDIENTES } }

    override suspend fun markSynced(userId: String, id: Long, updatedAtEnviado: Long): Int {
        if (getById(userId, id)?.updatedAt != updatedAtEnviado) return 0
        return modificar(userId, id) { it.copy(syncStatus = SyncStatus.SYNCED) }
    }

    override suspend fun markFailed(userId: String, id: Long, updatedAtEnviado: Long): Int {
        if (getById(userId, id)?.updatedAt != updatedAtEnviado) return 0
        return modificar(userId, id) { it.copy(syncStatus = SyncStatus.FAILED) }
    }

    override suspend fun deleteIfPendingDelete(userId: String, id: Long): Int {
        if (getById(userId, id)?.syncStatus != SyncStatus.PENDING_DELETE) return 0
        return modificar(userId, id) { null }
    }

    override suspend fun retryFailed(userId: String, id: Long): Int {
        if (getById(userId, id)?.syncStatus != SyncStatus.FAILED) return 0
        return modificar(userId, id) {
            it.copy(syncStatus = if (it.remoteId == null) SyncStatus.PENDING_CREATE else SyncStatus.PENDING_UPDATE)
        }
    }

    override suspend fun retryAllFailed(userId: String): Int = getFailed(userId).sumOf { retryFailed(userId, it.id) }
}

// Nube falsa en memoria: documentos por usuario (uid -> clientId -> documento),
// igual que la ruta users/{uid}/reminders/{clientId}. Permite simular fallas.
class FakeCloudReminderDataSource : CloudReminderDataSource {
    private val porUsuario = mutableMapOf<String, MutableMap<String, CloudReminder>>()
    fun documentos(uid: String): MutableMap<String, CloudReminder> = porUsuario.getOrPut(uid) { linkedMapOf() }

    var upserts = 0
        private set
    var deletes = 0
        private set

    // Si no es null, TODAS las llamadas fallan con este error (por ejemplo SinConexion)
    var falla: CloudException? = null

    // Fallas solo para ciertos documentos (por ejemplo PermissionDenied en una fila)
    val fallaPorClientId = mutableMapOf<String, CloudException>()

    // La escritura llega a la nube pero la app recibe SinConexion (respuesta perdida / timeout)
    var perderRespuesta = false

    // Permite frenar una llamada a mitad de camino (prueba del Mutex y de cancelación)
    var bloqueo: CompletableDeferred<Unit>? = null

    // Se ejecuta DESPUÉS de cada operación exitosa en la nube y antes de devolver el control
    // (simula, por ejemplo, un cambio de sesión mientras se esperaba la respuesta)
    var despuesDeOperar: suspend (operacion: String) -> Unit = {}

    private suspend fun antes(clientId: String? = null) {
        bloqueo?.await()
        falla?.let { throw it }
        clientId?.let { id -> fallaPorClientId[id]?.let { throw it } }
    }

    override suspend fun upsert(uid: String, reminder: CloudReminder) {
        antes(reminder.clientId)
        upserts++
        documentos(uid)[reminder.clientId] = reminder
        despuesDeOperar("upsert")
        if (perderRespuesta) throw CloudException.SinConexion()
    }

    override suspend fun delete(uid: String, clientId: String) {
        antes(clientId)
        deletes++
        documentos(uid).remove(clientId)
        despuesDeOperar("delete")
        if (perderRespuesta) throw CloudException.SinConexion()
    }

    override suspend fun fetchAll(uid: String): List<CloudReminder> {
        antes()
        val copia = documentos(uid).values.toList()
        despuesDeOperar("fetchAll")
        return copia
    }
}

// Conectividad observable: cambiar `conectado` emite en estaConectado (simula la reconexión)
class FakeConnectivityObserver(conectado: Boolean = false) : ConnectivityObserver {
    private val estado = MutableStateFlow(conectado)
    var conectado: Boolean
        get() = estado.value
        set(valor) { estado.value = valor }
    override val estaConectado: Flow<Boolean> = estado
    override fun hayConexionAhora() = estado.value
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
