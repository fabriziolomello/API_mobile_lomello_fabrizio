package com.fabrizio.recordatorios

import com.fabrizio.recordatorios.data.auth.AuthUser
import com.fabrizio.recordatorios.data.auth.CierreSesion
import com.fabrizio.recordatorios.data.local.ReminderEntity
import com.fabrizio.recordatorios.data.local.SyncStatus
import com.fabrizio.recordatorios.data.sync.SyncManager
import com.fabrizio.recordatorios.data.sync.seguirLaSesion
import com.fabrizio.recordatorios.model.Categoria
import com.fabrizio.recordatorios.model.Prioridad
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// AppContainer.vincularSincronizacionASesion: perder la sesión (por cualquier motivo)
// borra los datos locales del usuario
class VinculoSesionTest {

    private val dao = FakeReminderDao()
    private val prefs = FakePreferenciasRepository()
    private val auth = FakeAuthRepository(AuthUser("uid-1", "ana@mail.com"))
    private var syncIniciados = 0
    private var limpiezas = 0
    private val syncManager = SyncManager(
        dao = dao,
        nube = FakeCloudReminderDataSource(),
        conectividad = FakeConnectivityObserver(conectado = false),
        appScope = CoroutineScope(Dispatchers.Unconfined),
        enTransaccion = { it() },
        registrarSincronizacion = prefs::guardarUltimaSincronizacion,
        usuarioActual = { auth.sesion.value?.uid }
    )
    private val cierre = CierreSesion(
        dao = dao,
        syncManager = syncManager,
        detenerSincronizacion = {},
        borrarBaseLocal = {
            limpiezas++
            dao.filas.value = emptyList()
        },
        preferencias = prefs,
        auth = auth
    )

    private suspend fun crearFila(clientId: String) = dao.insert(
        ReminderEntity(
            clientId = clientId, userId = "uid-1", title = "Estudiar", category = Categoria.Estudio,
            priority = Prioridad.Alta, createdAt = 1_000, updatedAt = 1_000, syncStatus = SyncStatus.SYNCED
        )
    )

    // Corre seguirLaSesion en paralelo mientras se ejecuta el bloque
    private fun conVinculo(
        alPerderSesion: suspend () -> Unit = cierre::limpiarDatosLocales,
        bloque: suspend () -> Unit
    ) = runBlocking {
        val vinculo = launch(Dispatchers.Unconfined) {
            seguirLaSesion(auth.currentUser, { syncIniciados++ }, alPerderSesion)
        }
        bloque()
        vinculo.cancelAndJoin()
    }

    @Test
    fun sesionQuePasaAFalseLimpiaRoomYDataStore() = conVinculo {
        crearFila("a")
        prefs.guardarUltimaSincronizacion(1_234L)
        assertEquals(1, syncIniciados)

        // Firebase cierra la sesión solo (cuenta deshabilitada, token inválido, ...)
        auth.sesion.value = null
        yield()

        assertTrue(dao.filas.value.isEmpty())
        assertNull(prefs.ultima.value)
        assertTrue(prefs.datosDeSesionBorrados)
    }

    @Test
    fun logoutManualSeguidoDeLaLimpiezaDelVinculoEsIdempotente() = conVinculo {
        crearFila("a")

        cierre.cerrarSesion() // limpia, hace logout, y el vínculo vuelve a limpiar
        yield()

        assertEquals(2, limpiezas)
        assertTrue(dao.filas.value.isEmpty())
        assertNull(auth.sesion.value)
    }

    @Test
    fun siLaLimpiezaFallaSigueEscuchandoLaSesion() {
        var intentos = 0
        conVinculo(alPerderSesion = { intentos++; throw IllegalStateException("disco lleno") }) {
            auth.sesion.value = null
            yield()
            auth.sesion.value = AuthUser("uid-2", "beto@mail.com")
            yield()
            auth.sesion.value = null
            yield()
        }

        assertEquals(2, intentos)
        assertEquals(2, syncIniciados)
    }

    @Test
    fun cambiosDentroDeLaMismaSesionNoLimpian() = conVinculo {
        crearFila("a")
        auth.sesion.value = AuthUser("uid-1", "nuevo@mail.com") // sigue con sesión
        yield()

        assertFalse(prefs.datosDeSesionBorrados)
        assertEquals(1, dao.filas.value.size)
        assertEquals(1, syncIniciados)
    }
}
