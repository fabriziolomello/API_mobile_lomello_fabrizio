package com.fabrizio.recordatorios.data.sync

import com.fabrizio.recordatorios.data.local.ReminderDao
import com.fabrizio.recordatorios.data.local.ReminderEntity
import com.fabrizio.recordatorios.data.local.SyncStatus
import com.fabrizio.recordatorios.data.mapper.actualizarDesde
import com.fabrizio.recordatorios.data.mapper.toDto
import com.fabrizio.recordatorios.data.mapper.toEntity
import com.fabrizio.recordatorios.data.remote.DataResult
import com.fabrizio.recordatorios.data.remote.ReminderDto
import com.fabrizio.recordatorios.data.remote.ReminderRemoteDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.atomic.AtomicBoolean

// Sincroniza Room con el servidor en dos pasos:
//   1. PUSH: envía las operaciones pendientes (crear / actualizar / borrar).
//   2. PULL: baja la lista del servidor y la mezcla en Room sin duplicar.
// Room sigue siendo la fuente de verdad: la UI se entera de todo por los Flow del DAO.
class SyncManager(
    private val dao: ReminderDao,
    private val remoto: ReminderRemoteDataSource,
    private val conectividad: ConnectivityObserver,
    // Scope de la aplicación: un sync no se corta porque el usuario cambie de pantalla
    private val appScope: CoroutineScope,
    // Agrupa las escrituras del PULL en una transacción de Room
    private val enTransaccion: suspend (suspend () -> Unit) -> Unit,
    // Guarda en DataStore la hora del último sync exitoso
    private val registrarSincronizacion: suspend (Long) -> Unit,
    private val reloj: () -> Long = System::currentTimeMillis
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

    // Se llama una vez al iniciar la app: cada vez que vuelve la red, sincroniza.
    // (El Flow emite true al arrancar si hay conexión, así también sincroniza al abrir la app.)
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
        if (!mutex.tryLock()) {
            otraVuelta.set(true)
            return SyncResult.EnCurso
        }
        try {
            _estado.value = EstadoSincronizacion.Sincronizando
            var resultado: SyncResult
            do {
                otraVuelta.set(false)
                resultado = unaVuelta()
            } while (otraVuelta.get() && resultado is SyncResult.Exito)

            _estado.value = estadoTras(resultado)
            return resultado
        } finally {
            mutex.unlock()
        }
    }

    private fun estadoTras(resultado: SyncResult): EstadoSincronizacion = when {
        resultado is SyncResult.Fallo ->
            EstadoSincronizacion.Error(resultado.error.mensajeUsuario, reloj())
        resultado is SyncResult.Exito && resultado.rechazados > 0 -> EstadoSincronizacion.Error(
            "${resultado.rechazados} cambio(s) fueron rechazados por el servidor. Tocá Reintentar.",
            reloj()
        )
        else -> EstadoSincronizacion.Inactivo
    }

    private suspend fun unaVuelta(): SyncResult {
        // --- 1. PUSH ---
        var enviados = 0
        var rechazados = 0
        for (pendiente in dao.getPendingSync()) {
            when (val resultado = enviar(pendiente)) {
                is DataResult.Exito -> enviados++
                is DataResult.Error ->
                    if (resultado.recuperable) {
                        // Red caída, timeout o 5xx: se corta el sync; todo sigue pendiente
                        // en Room y se reintenta en el próximo evento (reconexión o manual).
                        return SyncResult.Fallo(resultado)
                    } else {
                        // 4xx: reenviar lo mismo no sirve. Queda FAILED para reintento manual
                        // (salvo que el usuario lo haya vuelto a editar mientras tanto).
                        dao.markFailed(pendiente.id, pendiente.updatedAt)
                        rechazados++
                    }
            }
        }

        // --- 2. PULL ---
        val remotos = when (val resultado = remoto.obtenerTodos()) {
            is DataResult.Exito -> resultado.datos
            is DataResult.Error -> return SyncResult.Fallo(resultado)
        }
        val recibidos = mezclar(remotos)

        registrarSincronizacion(reloj())
        return SyncResult.Exito(enviados, recibidos, rechazados)
    }

    // Envía una operación pendiente. Cada rama confirma en Room SIN crear filas nuevas.
    private suspend fun enviar(entidad: ReminderEntity): DataResult<Unit> = when (entidad.syncStatus) {
        SyncStatus.PENDING_CREATE -> enviarCreacion(entidad)
        SyncStatus.PENDING_UPDATE -> enviarActualizacion(entidad)
        SyncStatus.PENDING_DELETE -> enviarBorrado(entidad)
        SyncStatus.SYNCED, SyncStatus.FAILED -> DataResult.Exito(Unit) // no se reenvían
    }

    private suspend fun enviarCreacion(entidad: ReminderEntity): DataResult<Unit> {
        // IDEMPOTENCIA: antes de crear se pregunta si el servidor ya tiene este clientId.
        // Pasa si un POST anterior llegó pero la respuesta se perdió (timeout, corte de red).
        val existente = when (val busqueda = remoto.buscarPorClientId(entidad.clientId)) {
            is DataResult.Exito -> busqueda.datos
            is DataResult.Error -> return busqueda
        }
        val confirmado: ReminderDto = when (
            val resultado = if (existente?.id != null) {
                // Ya existe: se adopta ese registro y se le envían los datos locales más nuevos
                remoto.actualizar(existente.id, entidad.toDto().copy(id = existente.id))
            } else {
                remoto.crear(entidad.toDto())
            }
        ) {
            is DataResult.Exito -> resultado.datos
            is DataResult.Error -> return resultado
        }
        val remoteId = confirmado.id ?: existente?.id ?: return DataResult.Desconocido(
            IllegalStateException("El servidor no devolvió un id")
        )
        confirmarCreacion(entidad, remoteId)
        return DataResult.Exito(Unit)
    }

    // Guarda el remoteId en la MISMA fila (por id local). Nunca inserta.
    private suspend fun confirmarCreacion(entidad: ReminderEntity, remoteId: String) {
        if (dao.markSynced(entidad.id, remoteId, entidad.updatedAt) > 0) return
        // La fila cambió durante el envío: se guarda el remoteId y queda como actualización
        if (dao.assignRemoteId(entidad.id, remoteId) > 0) return
        // La fila ya no existe (el usuario la borró mientras viajaba el POST):
        // se borra también en el servidor para no dejar un registro huérfano.
        remoto.eliminar(remoteId)
    }

    private suspend fun enviarActualizacion(entidad: ReminderEntity): DataResult<Unit> {
        val remoteId = entidad.remoteId ?: return enviarCreacion(entidad)
        return when (val resultado = remoto.actualizar(remoteId, entidad.toDto())) {
            is DataResult.Exito -> {
                // Si el usuario volvió a editar mientras tanto, sigue pendiente (0 filas)
                dao.markSynced(entidad.id, remoteId, entidad.updatedAt)
                DataResult.Exito(Unit)
            }
            is DataResult.Error ->
                if (resultado is DataResult.ErrorCliente && resultado.codigo == 404) {
                    // El servidor ya no lo tiene (lo borraron desde el panel). Se vuelve
                    // a crear; la búsqueda por clientId evita duplicarlo si ya estaba.
                    dao.resetToPendingCreate(entidad.id)
                    enviarCreacion(entidad.copy(remoteId = null, syncStatus = SyncStatus.PENDING_CREATE))
                } else {
                    resultado
                }
        }
    }

    private suspend fun enviarBorrado(entidad: ReminderEntity): DataResult<Unit> {
        val remoteId = entidad.remoteId
        if (remoteId != null) {
            // eliminar() ya trata el 404 como éxito (DELETE idempotente)
            val resultado = remoto.eliminar(remoteId)
            if (resultado is DataResult.Error) return resultado
        }
        dao.deleteIfPendingDelete(entidad.id)
        return DataResult.Exito(Unit)
    }

    // Mezcla la lista del servidor en Room. Devuelve cuántas filas se insertaron o cambiaron.
    // Regla: los cambios locales pendientes ganan (se enviarán en el próximo push);
    // las filas ya sincronizadas toman los datos del servidor.
    private suspend fun mezclar(remotos: List<ReminderDto>): Int {
        var cambios = 0
        enTransaccion {
            val ahora = reloj()
            val idsRemotos = mutableSetOf<String>()
            for (dto in remotos) {
                val nueva = dto.toEntity(ahora) ?: continue // DTO inválido: se descarta
                val remoteId = nueva.remoteId ?: continue
                idsRemotos += remoteId

                // Se busca primero por remoteId y después por clientId: así un registro
                // que el servidor ya tiene nunca se inserta dos veces.
                val local = dao.getByRemoteId(remoteId) ?: dao.getByClientId(nueva.clientId)
                when {
                    local == null -> {
                        dao.insert(nueva)
                        cambios++
                    }
                    // Creación local cuyo POST llegó al servidor pero no se confirmó:
                    // se adopta el remoteId y los datos locales se enviarán como actualización.
                    local.remoteId == null -> dao.assignRemoteId(local.id, remoteId)
                    local.syncStatus == SyncStatus.SYNCED -> {
                        val actualizada = local.actualizarDesde(dto)
                        if (actualizada != local) {
                            dao.update(actualizada)
                            cambios++
                        }
                    }
                    else -> Unit // pendiente o con error: se respeta el cambio local
                }
            }
            // Filas sincronizadas que ya no están en el servidor: se borraron allá
            dao.getWithRemoteId()
                .filter { it.syncStatus == SyncStatus.SYNCED && it.remoteId !in idsRemotos }
                .forEach {
                    dao.delete(it)
                    cambios++
                }
        }
        return cambios
    }
}
