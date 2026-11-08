package com.fabrizio.recordatorios

import com.fabrizio.recordatorios.data.auth.AuthUser
import com.fabrizio.recordatorios.data.auth.CierreSesion
import com.fabrizio.recordatorios.data.local.ReminderEntity
import com.fabrizio.recordatorios.data.local.SyncStatus
import com.fabrizio.recordatorios.data.remote.DataResult
import com.fabrizio.recordatorios.data.sync.SyncManager
import com.fabrizio.recordatorios.model.Categoria
import com.fabrizio.recordatorios.model.Prioridad
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CierreSesionTest {

    private val dao = FakeReminderDao()
    private val remoto = FakeRemoteDataSource()
    private val prefs = FakePreferenciasRepository()
    private val auth = FakeAuthRepository(AuthUser("uid-1", "ana@mail.com"))
    private val pasos = mutableListOf<String>()
    private val syncManager = SyncManager(
        dao = dao,
        remoto = remoto,
        conectividad = FakeConnectivityObserver(conectado = true),
        appScope = CoroutineScope(Dispatchers.Unconfined),
        enTransaccion = { it() },
        registrarSincronizacion = prefs::guardarUltimaSincronizacion,
        reloj = { 5_000L }
    )

    private fun cierre(borrarBase: suspend () -> Unit = { dao.filas.value = emptyList() }) = CierreSesion(
        dao = dao,
        syncManager = syncManager,
        detenerSincronizacion = { pasos += "detener" },
        borrarBaseLocal = {
            pasos += "borrarBase"
            borrarBase()
        },
        preferencias = prefs,
        auth = auth
    )

    private suspend fun crearPendiente(clientId: String) = dao.insert(
        ReminderEntity(
            clientId = clientId, title = "Estudiar", category = Categoria.Estudio, priority = Prioridad.Alta,
            createdAt = 1_000, updatedAt = 1_000, syncStatus = SyncStatus.PENDING_CREATE
        )
    )

    @Test
    fun conRedSubeLoPendienteYNoQuedaNada() = runBlocking {
        crearPendiente("a")
        crearPendiente("b")

        val sinSincronizar = cierre().sincronizarAntesDeSalir()

        assertEquals(0, sinSincronizar)
        assertEquals(2, remoto.servidor.size)
    }

    @Test
    fun sinRedCuentaLosPendientes() = runBlocking {
        crearPendiente("a")
        crearPendiente("b")
        remoto.falla = DataResult.SinConexion

        assertEquals(2, cierre().sincronizarAntesDeSalir())
    }

    @Test
    fun losRechazadosTambienCuentanComoSinSincronizar() = runBlocking {
        crearPendiente("a")
        remoto.falla = DataResult.ErrorCliente(400) // 4xx: queda FAILED

        assertEquals(1, cierre().sincronizarAntesDeSalir())
    }

    @Test
    fun cerrarSesionLimpiaTodoYRecienDespuesHaceLogout() = runBlocking {
        crearPendiente("a")
        prefs.guardarUltimaSincronizacion(1_234L)
        auth.alCerrarSesion = {
            pasos += "logout"
            // Al llegar al logout, los datos locales ya tienen que estar borrados
            assertTrue(dao.filas.value.isEmpty())
            assertTrue(prefs.datosDeSesionBorrados)
        }

        cierre().cerrarSesion()

        assertEquals(listOf("detener", "borrarBase", "logout"), pasos)
        assertNull(prefs.ultima.value)
        assertNull(auth.sesion.value)
    }

    @Test
    fun siFallaLaLimpiezaNoSeCierraLaSesion() = runBlocking {
        val cierre = cierre(borrarBase = { throw IllegalStateException("disco lleno") })

        try {
            cierre.cerrarSesion()
            fail("Debía propagar el error")
        } catch (e: IllegalStateException) {
            // esperado
        }

        assertNotNull(auth.sesion.value) // la sesión sigue abierta: se puede reintentar
        assertFalse(prefs.datosDeSesionBorrados)
    }
}
