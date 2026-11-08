package com.fabrizio.recordatorios

import com.fabrizio.recordatorios.data.LocalFirstReminderRepository
import com.fabrizio.recordatorios.data.RecordatorioNoEncontradoException
import com.fabrizio.recordatorios.data.SesionRequeridaException
import com.fabrizio.recordatorios.data.auth.AuthUser
import com.fabrizio.recordatorios.data.local.SyncStatus
import com.fabrizio.recordatorios.data.sync.SyncManager
import com.fabrizio.recordatorios.model.Categoria
import com.fabrizio.recordatorios.model.Estado
import com.fabrizio.recordatorios.model.Prioridad
import com.fabrizio.recordatorios.model.Recordatorio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LocalFirstReminderRepositoryTest {

    private val ana = AuthUser("uid-ana", "ana@mail.com")
    private val beto = AuthUser("uid-beto", "beto@mail.com")

    private val dao = FakeReminderDao()
    private val auth = FakeAuthRepository(ana)
    private var ahora = 1_000L
    // Sin conexión: los cambios quedan solo en Room (no se dispara ningún sync)
    private val syncManager = SyncManager(
        dao = dao,
        remoto = FakeRemoteDataSource(),
        conectividad = FakeConnectivityObserver(conectado = false),
        appScope = CoroutineScope(Dispatchers.Unconfined),
        enTransaccion = { it() },
        registrarSincronizacion = {},
        usuarioActual = { auth.sesion.value?.uid }
    )
    private val repositorio = LocalFirstReminderRepository(
        dao = dao,
        syncManager = syncManager,
        preferencias = FakePreferenciasRepository(),
        auth = auth,
        reloj = { ahora },
        generarClientId = { "uuid-${dao.filas.value.size + 1}" }
    )

    private val nuevo = Recordatorio(
        id = 0,
        titulo = "Estudiar Room",
        categoria = Categoria.Estudio,
        prioridad = Prioridad.Alta,
        estado = Estado.Pendiente
    )

    @Test
    fun crearGuardaPendienteDeCrearConClientIdYDuenio() = runBlocking {
        val id = repositorio.crear(nuevo)
        val fila = dao.getById(ana.uid, id)!!
        assertEquals(SyncStatus.PENDING_CREATE, fila.syncStatus)
        assertEquals("uuid-1", fila.clientId)
        assertEquals(ana.uid, fila.userId)
        assertNull(fila.remoteId)
    }

    @Test
    fun editarAlgoNuncaSincronizadoSigueSiendoCreacionYNoDuplica() = runBlocking {
        val id = repositorio.crear(nuevo)
        ahora = 2_000L
        repositorio.actualizar(nuevo.copy(id = id, titulo = "Estudiar Room a fondo"))

        val fila = dao.getById(ana.uid, id)!!
        assertEquals(1, dao.filas.value.size)
        assertEquals(SyncStatus.PENDING_CREATE, fila.syncStatus)
        assertEquals("uuid-1", fila.clientId)
        assertEquals(2_000L, fila.updatedAt)
    }

    @Test
    fun editarAlgoSincronizadoQuedaPendienteDeActualizar() = runBlocking {
        val id = repositorio.crear(nuevo)
        dao.update(dao.getById(ana.uid, id)!!.copy(remoteId = "42", syncStatus = SyncStatus.SYNCED))

        repositorio.cambiarCompletado(id, true)

        val fila = dao.getById(ana.uid, id)!!
        assertEquals(SyncStatus.PENDING_UPDATE, fila.syncStatus)
        assertTrue(fila.isCompleted)
        assertEquals("42", fila.remoteId)
    }

    @Test
    fun eliminarSinRemoteIdBorraLaFila() = runBlocking {
        val id = repositorio.crear(nuevo)
        repositorio.eliminar(id)
        assertNull(dao.getById(ana.uid, id))
    }

    @Test
    fun eliminarConRemoteIdEsBorradoLogicoYSeOcultaEnLaUi() = runBlocking {
        val id = repositorio.crear(nuevo)
        dao.update(dao.getById(ana.uid, id)!!.copy(remoteId = "42", syncStatus = SyncStatus.SYNCED))

        repositorio.eliminar(id)

        assertEquals(SyncStatus.PENDING_DELETE, dao.getById(ana.uid, id)!!.syncStatus)
        assertTrue(repositorio.observarRecordatorios().first().isEmpty())
        assertNull(repositorio.observarRecordatorio(id).first())
    }

    // --- Aislamiento por usuario ---

    @Test
    fun elListadoCambiaAlCambiarDeSesion() = runBlocking {
        repositorio.crear(nuevo.copy(titulo = "De Ana"))
        auth.sesion.value = beto
        repositorio.crear(nuevo.copy(titulo = "De Beto"))

        assertEquals(listOf("De Beto"), repositorio.observarRecordatorios().first().map { it.titulo })
        auth.sesion.value = ana
        assertEquals(listOf("De Ana"), repositorio.observarRecordatorios().first().map { it.titulo })
        assertEquals(1, repositorio.cambiosPendientes.first())
    }

    @Test
    fun unUsuarioNoPuedeVerNiEditarNiBorrarLoDeOtro() = runBlocking {
        val idAna = repositorio.crear(nuevo)
        auth.sesion.value = beto

        assertNull(repositorio.observarRecordatorio(idAna).first())
        try {
            repositorio.actualizar(nuevo.copy(id = idAna, titulo = "Hackeado"))
            fail("Beto no debería poder editar un recordatorio de Ana")
        } catch (e: RecordatorioNoEncontradoException) {
            // esperado
        }
        repositorio.eliminar(idAna)

        assertEquals("Estudiar Room", dao.getById(ana.uid, idAna)!!.title)
    }

    @Test
    fun sinSesionElListadoEstaVacioYLasEscriturasFallan() = runBlocking {
        repositorio.crear(nuevo)
        auth.sesion.value = null

        assertTrue(repositorio.observarRecordatorios().first().isEmpty())
        assertEquals(0, repositorio.cambiosPendientes.first())
        try {
            repositorio.crear(nuevo)
            fail("Sin sesión no se debe guardar nada")
        } catch (e: SesionRequeridaException) {
            // esperado
        }
        assertEquals(1, dao.filas.value.size) // no se guardó ninguna fila con uid vacío
    }
}
