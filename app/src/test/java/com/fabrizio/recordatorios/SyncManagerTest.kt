package com.fabrizio.recordatorios

import com.fabrizio.recordatorios.data.LocalFirstReminderRepository
import com.fabrizio.recordatorios.data.auth.AuthUser
import com.fabrizio.recordatorios.data.cloud.CloudException
import com.fabrizio.recordatorios.data.cloud.CloudReminder
import com.fabrizio.recordatorios.data.local.ReminderEntity
import com.fabrizio.recordatorios.data.local.SyncStatus
import com.fabrizio.recordatorios.data.sync.EstadoSincronizacion
import com.fabrizio.recordatorios.data.sync.SyncManager
import com.fabrizio.recordatorios.data.sync.SyncResult
import com.fabrizio.recordatorios.model.Categoria
import com.fabrizio.recordatorios.model.Estado
import com.fabrizio.recordatorios.model.Prioridad
import com.fabrizio.recordatorios.model.Recordatorio
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ANA = "uid-ana"
private const val BETO = "uid-beto"

class SyncManagerTest {

    private val dao = FakeReminderDao()
    private val nube = FakeCloudReminderDataSource()
    private val prefs = FakePreferenciasRepository()
    private val conectividad = FakeConnectivityObserver(conectado = true)
    private var usuario: String? = ANA
    private var ahora = 5_000L
    private val avisos = mutableListOf<String>()
    private var cierresDeSesion = 0
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private val syncManager = SyncManager(
        dao = dao,
        nube = nube,
        conectividad = conectividad,
        appScope = scope,
        enTransaccion = { it() },
        registrarSincronizacion = prefs::guardarUltimaSincronizacion,
        usuarioActual = { usuario },
        reloj = { ahora },
        log = { avisos += it },
        alSesionInvalida = { cierresDeSesion++ }
    )

    // Repositorio real sobre el mismo DAO (para crear/editar/borrar como lo hace la app)
    private val auth = FakeAuthRepository(AuthUser(ANA, "ana@mail.com"))
    private val repositorio = LocalFirstReminderRepository(
        dao = dao,
        syncManager = syncManager,
        preferencias = prefs,
        auth = auth,
        reloj = { ahora },
        generarClientId = { "uuid-${dao.filas.value.size + 1}" }
    )

    @After
    fun cerrar() = scope.cancel()

    private suspend fun crearLocal(
        clientId: String,
        status: SyncStatus = SyncStatus.PENDING_CREATE,
        updatedAt: Long = 1_000L,
        title: String = "Estudiar",
        userId: String = ANA
    ) = dao.insert(
        ReminderEntity(
            clientId = clientId, userId = userId, title = title, category = Categoria.Estudio,
            priority = Prioridad.Alta, createdAt = 1_000L, updatedAt = updatedAt, syncStatus = status
        )
    )

    private fun doc(clientId: String, title: String = "Estudiar", updatedAt: Long = 1_000L) = CloudReminder(
        clientId = clientId, title = title, description = null, category = Categoria.Estudio,
        priority = Prioridad.Alta, completed = false, dueDate = null, createdAt = 1_000L, updatedAt = updatedAt
    )

    private suspend fun fila(clientId: String, userId: String = ANA) = dao.getByClientId(userId, clientId)

    // --- Push ---

    @Test
    fun crearOfflineQuedaPendienteYAlVolverLaConexionSubeYQuedaSynced() = runBlocking {
        conectividad.conectado = false
        syncManager.iniciar() // escucha la reconexión, como al iniciar sesión
        val id = repositorio.crear(
            Recordatorio(0, "Comprar libros", Categoria.Estudio, Prioridad.Media, Estado.Pendiente)
        )
        assertEquals(SyncStatus.PENDING_CREATE, dao.getById(ANA, id)!!.syncStatus)
        assertTrue(nube.documentos(ANA).isEmpty())

        conectividad.conectado = true // vuelve la red: dispara el sync

        assertEquals(SyncStatus.SYNCED, dao.getById(ANA, id)!!.syncStatus)
        assertEquals("Comprar libros", nube.documentos(ANA)["uuid-1"]!!.title)
        assertEquals(5_000L, prefs.ultima.value)
        assertEquals(EstadoSincronizacion.Inactivo, syncManager.estado.value)
    }

    @Test
    fun reintentarElPushDosVecesNoDuplica() = runBlocking {
        crearLocal("c1")
        nube.perderRespuesta = true // el upsert llega, pero la app recibe SinConexion

        assertEquals(SyncResult.SinConexion, syncManager.sincronizar())
        assertEquals(SyncStatus.PENDING_CREATE, fila("c1")!!.syncStatus)

        nube.perderRespuesta = false
        syncManager.sincronizar()

        assertEquals(2, nube.upserts)
        assertEquals(1, nube.documentos(ANA).size) // mismo documento (ID = clientId)
        assertEquals(1, dao.filas.value.size)
        assertEquals(SyncStatus.SYNCED, fila("c1")!!.syncStatus)
    }

    @Test
    fun editarOfflineSubeLaVersionNueva() = runBlocking {
        val id = crearLocal("c1", status = SyncStatus.SYNCED)
        nube.documentos(ANA)["c1"] = doc("c1")
        conectividad.conectado = false

        ahora = 6_000L
        repositorio.actualizar(dao.getById(ANA, id)!!.let {
            Recordatorio(id, "Estudiar a fondo", it.category, it.priority, Estado.Pendiente)
        })
        assertEquals(SyncStatus.PENDING_UPDATE, fila("c1")!!.syncStatus)

        conectividad.conectado = true
        syncManager.sincronizar()

        assertEquals("Estudiar a fondo", nube.documentos(ANA)["c1"]!!.title)
        assertEquals(6_000L, nube.documentos(ANA)["c1"]!!.updatedAt)
        assertEquals(SyncStatus.SYNCED, fila("c1")!!.syncStatus)
    }

    @Test
    fun editarYBorrarOfflineElBorradoLlegaALaNube() = runBlocking {
        val id = crearLocal("c1", status = SyncStatus.SYNCED)
        nube.documentos(ANA)["c1"] = doc("c1")
        conectividad.conectado = false

        ahora = 6_000L
        repositorio.cambiarCompletado(id, true)
        ahora = 7_000L
        repositorio.eliminar(id)
        assertEquals(SyncStatus.PENDING_DELETE, fila("c1")!!.syncStatus)
        assertEquals(0, nube.deletes) // offline: nada salió todavía

        conectividad.conectado = true
        syncManager.sincronizar()

        assertEquals(1, nube.deletes)
        assertTrue(nube.documentos(ANA).isEmpty())
        assertNull(fila("c1")) // fila física borrada recién después de confirmar
    }

    // --- Pull ---

    @Test
    fun pullInsertaDocumentosNuevosSinDuplicar() = runBlocking {
        nube.documentos(ANA)["c1"] = doc("c1", title = "Creado en otro dispositivo")

        val primero = syncManager.sincronizar()
        syncManager.sincronizar()

        assertEquals(SyncResult.Exito(enviados = 0, recibidos = 1, rechazados = 0), primero)
        assertEquals(1, dao.filas.value.size)
        val insertada = fila("c1")!!
        assertEquals("Creado en otro dispositivo", insertada.title)
        assertEquals(SyncStatus.SYNCED, insertada.syncStatus)
        assertEquals(ANA, insertada.userId)
        assertNull(insertada.remoteId)
    }

    @Test
    fun pullPropagaUnBorradoRemotoSoloEnFilasSynced() = runBlocking {
        crearLocal("sigue", status = SyncStatus.SYNCED)
        crearLocal("borrado-en-otro-dispositivo", status = SyncStatus.SYNCED)
        crearLocal("fallido", status = SyncStatus.FAILED)
        nube.documentos(ANA)["sigue"] = doc("sigue")

        syncManager.sincronizar()

        assertTrue(fila("sigue") != null)
        assertNull(fila("borrado-en-otro-dispositivo"))
        // FAILED tiene cambios sin subir: no se borra aunque no esté en la nube
        assertEquals(SyncStatus.FAILED, fila("fallido")!!.syncStatus)
    }

    @Test
    fun pullActualizaUnaFilaSyncedSoloSiLaNubeEsMasNueva() = runBlocking {
        crearLocal("nueva", status = SyncStatus.SYNCED, updatedAt = 1_000L, title = "Local")
        crearLocal("vieja", status = SyncStatus.SYNCED, updatedAt = 4_000L, title = "Local")
        nube.documentos(ANA)["nueva"] = doc("nueva", title = "Nube", updatedAt = 3_000L)
        nube.documentos(ANA)["vieja"] = doc("vieja", title = "Nube", updatedAt = 2_000L)

        syncManager.sincronizar()

        assertEquals("Nube", fila("nueva")!!.title)
        assertEquals("Local", fila("vieja")!!.title)
    }

    // --- Conflictos (el pull corre ANTES que el push) ---

    @Test
    fun pendienteLocalMasViejoQueLaNubeGanaLaNubeYElPushNoLaPisa() = runBlocking {
        crearLocal("c1", status = SyncStatus.PENDING_UPDATE, updatedAt = 2_000L, title = "Local viejo")
        nube.documentos(ANA)["c1"] = doc("c1", title = "Otro dispositivo", updatedAt = 3_000L)

        val resultado = syncManager.sincronizar()

        assertEquals(SyncResult.Exito(enviados = 0, recibidos = 1, rechazados = 0), resultado)
        assertEquals("Otro dispositivo", fila("c1")!!.title)
        assertEquals(SyncStatus.SYNCED, fila("c1")!!.syncStatus)
        // El push no subió la versión vieja: la nube conserva la suya
        assertEquals(0, nube.upserts)
        assertEquals("Otro dispositivo", nube.documentos(ANA)["c1"]!!.title)
        assertEquals(1, dao.filas.value.size)
    }

    @Test
    fun pendienteLocalMasNuevoQueLaNubeSeSube() = runBlocking {
        crearLocal("c1", status = SyncStatus.PENDING_UPDATE, updatedAt = 4_000L, title = "Local nuevo")
        nube.documentos(ANA)["c1"] = doc("c1", title = "Otro dispositivo", updatedAt = 3_000L)

        val resultado = syncManager.sincronizar()

        assertEquals(SyncResult.Exito(enviados = 1, recibidos = 0, rechazados = 0), resultado)
        assertEquals("Local nuevo", nube.documentos(ANA)["c1"]!!.title)
        assertEquals(4_000L, nube.documentos(ANA)["c1"]!!.updatedAt)
        assertEquals(SyncStatus.SYNCED, fila("c1")!!.syncStatus)
    }

    @Test
    fun empateDeUpdatedAtGanaLoLocal() = runBlocking {
        crearLocal("c1", status = SyncStatus.PENDING_UPDATE, updatedAt = 3_000L, title = "Local")
        nube.documentos(ANA)["c1"] = doc("c1", title = "Nube", updatedAt = 3_000L)

        syncManager.sincronizar()

        assertEquals("Local", nube.documentos(ANA)["c1"]!!.title)
        assertEquals("Local", fila("c1")!!.title)
    }

    @Test
    fun edicionLocalContraBorradoRemotoGanaLaEdicion() = runBlocking {
        // Límite documentado: el documento se borró en otro dispositivo, pero acá hay una
        // edición pendiente. No es SYNCED, así que el pull no la borra y el push la recrea.
        crearLocal("c1", status = SyncStatus.PENDING_UPDATE, title = "Editado acá")

        syncManager.sincronizar()

        assertEquals(SyncStatus.SYNCED, fila("c1")!!.syncStatus)
        assertEquals("Editado acá", nube.documentos(ANA)["c1"]!!.title)
    }

    @Test
    fun documentoInvalidoEnLaNubeNoBorraLaFilaSynced() = runBlocking {
        crearLocal("roto", status = SyncStatus.SYNCED, title = "Mi recordatorio")
        crearLocal("borrado", status = SyncStatus.SYNCED)
        nube.idsInvalidos(ANA) += "roto" // existe en la nube pero el mapper lo descartó

        syncManager.sincronizar()

        assertEquals("Mi recordatorio", fila("roto")!!.title) // no se tomó como borrado
        assertEquals(SyncStatus.SYNCED, fila("roto")!!.syncStatus)
        assertNull(fila("borrado")) // el que de verdad no está sí se borra
        assertEquals(1, avisos.count { it.contains("roto") })
    }

    @Test
    fun unBorradoLocalPendienteNoResucitaConElPull() = runBlocking {
        crearLocal("c1", status = SyncStatus.PENDING_DELETE)
        nube.documentos(ANA)["c1"] = doc("c1", updatedAt = 9_000L) // incluso más nuevo en la nube
        nube.fallaPorClientId["c1"] = CloudException.PermissionDenied() // el delete no pasa

        syncManager.sincronizar()

        // Sigue marcado para borrar (oculto en la UI), no FAILED ni pisado por la nube
        assertEquals(SyncStatus.PENDING_DELETE, fila("c1")!!.syncStatus)
        assertTrue(repositorio.observarRecordatorios().first().isEmpty())
    }

    // --- Errores ---

    @Test
    fun sinConexionCortaElPushYDejaTodoPendienteSinFailed() = runBlocking {
        crearLocal("c1", updatedAt = 1_000L)
        crearLocal("c2", updatedAt = 2_000L, status = SyncStatus.PENDING_UPDATE)
        crearLocal("c3", updatedAt = 3_000L, status = SyncStatus.PENDING_DELETE)
        nube.fallaPorClientId["c2"] = CloudException.SinConexion()

        val resultado = syncManager.sincronizar()

        assertEquals(SyncResult.SinConexion, resultado)
        assertEquals(SyncStatus.SYNCED, fila("c1")!!.syncStatus) // el que llegó antes del corte
        assertEquals(SyncStatus.PENDING_UPDATE, fila("c2")!!.syncStatus)
        assertEquals(SyncStatus.PENDING_DELETE, fila("c3")!!.syncStatus)
        assertTrue(dao.filas.value.none { it.syncStatus == SyncStatus.FAILED })
        assertEquals(0, nube.deletes) // el push se cortó: c3 ni se intentó
        assertNull(prefs.ultima.value)
        assertTrue(syncManager.estado.value is EstadoSincronizacion.Error)
    }

    @Test
    fun permissionDeniedMarcaFailedSigueConLasDemasYSePuedeReintentar() = runBlocking {
        crearLocal("rechazado", updatedAt = 1_000L)
        crearLocal("bien", updatedAt = 2_000L)
        nube.fallaPorClientId["rechazado"] = CloudException.PermissionDenied()

        val resultado = syncManager.sincronizar()

        assertEquals(SyncResult.Exito(enviados = 1, recibidos = 0, rechazados = 1), resultado)
        assertEquals(SyncStatus.FAILED, fila("rechazado")!!.syncStatus)
        assertEquals(SyncStatus.SYNCED, fila("bien")!!.syncStatus)
        assertTrue(syncManager.estado.value is EstadoSincronizacion.Error)

        // FAILED no se reenvía solo; con el reintento manual sí
        nube.fallaPorClientId.clear()
        syncManager.sincronizar()
        assertEquals(SyncStatus.FAILED, fila("rechazado")!!.syncStatus)
        repositorio.reintentarFallidos()
        assertEquals(SyncStatus.SYNCED, fila("rechazado")!!.syncStatus)
    }

    @Test
    fun siElPullFallaPorSinConexionNoHayPushYTodoQuedaPendiente() = runBlocking {
        crearLocal("c1")
        crearLocal("c2", status = SyncStatus.PENDING_DELETE)
        nube.fallaFetch = CloudException.SinConexion()

        val resultado = syncManager.sincronizar()

        assertEquals(SyncResult.SinConexion, resultado)
        assertEquals(0, nube.upserts) // las escrituras funcionarían, pero no se intentaron
        assertEquals(0, nube.deletes)
        assertEquals(SyncStatus.PENDING_CREATE, fila("c1")!!.syncStatus)
        assertEquals(SyncStatus.PENDING_DELETE, fila("c2")!!.syncStatus)
        assertTrue(dao.filas.value.none { it.syncStatus == SyncStatus.FAILED })
    }

    @Test
    fun siFallaElPullNoSeTocaNadaLocal() = runBlocking {
        crearLocal("local", status = SyncStatus.SYNCED)
        val antes = dao.filas.value
        nube.falla = CloudException.PermissionDenied()

        val resultado = syncManager.sincronizar()

        assertTrue(resultado is SyncResult.Fallo)
        assertEquals(antes, dao.filas.value) // no se borró la fila SYNCED aunque "no esté" en la nube
    }

    // --- Sesión y concurrencia ---

    @Test
    fun cambioDeUsuarioDuranteElPushNoEscribeEnRoom() = runBlocking {
        crearLocal("c1")
        // La sesión cambia mientras se esperaba la respuesta del upsert (el pull ya pasó)
        nube.despuesDeOperar = { operacion -> if (operacion == "upsert") usuario = BETO }

        val resultado = syncManager.sincronizar()

        assertEquals(SyncResult.SinSesion, resultado)
        assertEquals(SyncStatus.PENDING_CREATE, fila("c1")!!.syncStatus) // no se marcó SYNCED
        assertTrue(nube.documentos(BETO).isEmpty())
        assertTrue(dao.filas.value.none { it.userId == BETO })
        assertNull(prefs.ultima.value)
    }

    @Test
    fun cambioDeUsuarioDuranteElPullNoEscribeLosDatosEnLaOtraCuenta() = runBlocking {
        nube.documentos(ANA)["c1"] = doc("c1", title = "Privado de Ana")
        nube.despuesDeOperar = { operacion -> if (operacion == "fetchAll") usuario = BETO }

        val resultado = syncManager.sincronizar()

        assertEquals(SyncResult.SinSesion, resultado)
        assertTrue(dao.filas.value.isEmpty()) // nada de Ana quedó en Room (ni a nombre de Beto)
        assertNull(prefs.ultima.value)
    }

    @Test
    fun sinSesionNoSeEnviaNiSeBajaNada() = runBlocking {
        crearLocal("c1")
        nube.documentos(ANA)["c9"] = doc("c9")
        usuario = null

        assertEquals(SyncResult.SinSesion, syncManager.sincronizar())
        assertEquals(0, nube.upserts)
        assertEquals(1, dao.filas.value.size)
    }

    @Test
    fun soloSeSincronizanLasFilasDelUsuarioActual() = runBlocking {
        crearLocal("de-ana")
        crearLocal("de-beto", userId = BETO)

        syncManager.sincronizar()

        assertEquals(setOf("de-ana"), nube.documentos(ANA).keys)
        assertTrue(nube.documentos(BETO).isEmpty())
        assertEquals(SyncStatus.PENDING_CREATE, fila("de-beto", BETO)!!.syncStatus)
    }

    @Test
    fun soloCorreUnSyncALaVez() = runBlocking {
        crearLocal("c1")
        nube.bloqueo = CompletableDeferred()

        // El primer sync queda frenado en medio del upsert (tiene el Mutex)
        val primero = async(start = CoroutineStart.UNDISPATCHED) { syncManager.sincronizar() }
        // Un segundo evento (por ejemplo, otra reconexión) no lanza otro en paralelo
        assertEquals(SyncResult.EnCurso, syncManager.sincronizar())

        nube.bloqueo!!.complete(Unit)
        primero.await()
        assertEquals(1, nube.documentos(ANA).size)
        assertEquals(SyncStatus.SYNCED, fila("c1")!!.syncStatus)
    }

    @Test
    fun noAutenticadoEnElPushCierraLaSesion() = runBlocking {
        crearLocal("c1")
        nube.fallaPorClientId["c1"] = CloudException.NoAutenticado()

        val resultado = syncManager.sincronizar()

        assertTrue(resultado is SyncResult.Fallo)
        assertEquals(1, cierresDeSesion)
        assertEquals(SyncStatus.PENDING_CREATE, fila("c1")!!.syncStatus) // la fila no es culpable
    }

    @Test
    fun noAutenticadoEnElPullCierraLaSesion() = runBlocking {
        nube.fallaFetch = CloudException.NoAutenticado()

        syncManager.sincronizar()

        assertEquals(1, cierresDeSesion)
    }

    @Test
    fun permissionDeniedNoCierraLaSesion() = runBlocking {
        crearLocal("c1")
        nube.fallaPorClientId["c1"] = CloudException.PermissionDenied()
        syncManager.sincronizar()

        nube.fallaPorClientId.clear()
        nube.fallaFetch = CloudException.PermissionDenied()
        syncManager.sincronizar()

        assertEquals(0, cierresDeSesion)
        assertEquals(SyncStatus.FAILED, fila("c1")!!.syncStatus)
    }
}
