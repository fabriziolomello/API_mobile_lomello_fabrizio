package com.fabrizio.recordatorios

import com.fabrizio.recordatorios.data.cloud.CloudReminder
import com.fabrizio.recordatorios.data.cloud.cloudReminderDesdeDocumento
import com.fabrizio.recordatorios.data.cloud.toCloudReminder
import com.fabrizio.recordatorios.data.cloud.toEntity
import com.fabrizio.recordatorios.data.cloud.toMap
import com.fabrizio.recordatorios.data.local.ReminderEntity
import com.fabrizio.recordatorios.data.local.SyncStatus
import com.fabrizio.recordatorios.model.Categoria
import com.fabrizio.recordatorios.model.Prioridad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudMappersTest {

    private val descartes = mutableListOf<String>()
    private fun leer(id: String, datos: Map<String, Any?>) =
        cloudReminderDesdeDocumento(id, datos) { descartes += it }

    private val cloud = CloudReminder(
        clientId = "uuid-1",
        title = "Estudiar Firestore",
        description = "Capítulo 3",
        category = Categoria.Estudio,
        priority = Prioridad.Alta,
        completed = true,
        dueDate = 1_700_000_000_000L,
        createdAt = 1_000L,
        updatedAt = 2_000L
    )

    @Test
    fun idaYVueltaPorElMapaConservaTodo() {
        assertEquals(cloud, leer("uuid-1", cloud.toMap()))
        assertTrue(descartes.isEmpty())
    }

    @Test
    fun idaYVueltaConOpcionalesNulos() {
        val sinOpcionales = cloud.copy(description = null, dueDate = null)
        assertEquals(sinOpcionales, leer("uuid-1", sinOpcionales.toMap()))
    }

    @Test
    fun idaYVueltaPorLaEntidad() {
        val entidad = ReminderEntity(
            id = 7, clientId = "uuid-1", userId = "uid-ana", remoteId = null,
            title = "Estudiar Firestore", description = "Capítulo 3",
            category = Categoria.Estudio, priority = Prioridad.Alta, isCompleted = true,
            dueDate = 1_700_000_000_000L, createdAt = 1_000L, updatedAt = 2_000L,
            syncStatus = SyncStatus.PENDING_UPDATE
        )
        val subida = entidad.toCloudReminder()
        assertEquals(cloud, subida)

        val bajada = subida.toEntity("uid-ana")
        assertEquals(entidad.copy(id = 0, syncStatus = SyncStatus.SYNCED), bajada)
        assertEquals(SyncStatus.SYNCED, bajada.syncStatus)
        assertNull(bajada.remoteId)
    }

    @Test
    fun faltaUnCampoObligatorioDevuelveNullYLoRegistra() {
        val datos = cloud.toMap() - "updatedAt"
        assertNull(leer("uuid-1", datos))
        assertEquals(1, descartes.size)
        assertTrue(descartes.single().contains("updatedAt"))
    }

    @Test
    fun tipoIncorrectoEnObligatorioDevuelveNull() {
        assertNull(leer("uuid-1", cloud.toMap() + ("completed" to "sí")))
        assertNull(leer("uuid-1", cloud.toMap() + ("updatedAt" to "2000")))
        assertEquals(2, descartes.size)
    }

    @Test
    fun tipoIncorrectoEnOpcionalTambienDescartaElDocumento() {
        assertNull(leer("uuid-1", cloud.toMap() + ("dueDate" to "mañana")))
        assertTrue(descartes.single().contains("dueDate"))
    }

    @Test
    fun numerosComoLongYComoDouble() {
        val comoLong = leer("uuid-1", cloud.toMap() + ("updatedAt" to 2_000L) + ("dueDate" to 5L))
        val comoDouble = leer("uuid-1", cloud.toMap() + ("updatedAt" to 2_000.0) + ("dueDate" to 5.0))
        assertEquals(2_000L, comoLong!!.updatedAt)
        assertEquals(2_000L, comoDouble!!.updatedAt)
        assertEquals(5L, comoDouble.dueDate)
        assertEquals(comoLong, comoDouble)
    }

    @Test
    fun elIdDelDocumentoMandaSobreElCampoClientId() {
        val leido = leer("id-del-documento", cloud.toMap() + ("clientId" to "otro-id"))
        assertEquals("id-del-documento", leido!!.clientId)
    }

    @Test
    fun documentoSinCampoClientIdUsaElIdDelDocumento() {
        assertEquals("doc-9", leer("doc-9", cloud.toMap() - "clientId")!!.clientId)
    }

    @Test
    fun codigoDeCategoriaDesconocidoNoEsError() {
        val leido = leer("uuid-1", cloud.toMap() + ("category" to "INVENTADA"))
        assertNotNull(leido)
        assertEquals(Categoria.Personal, leido!!.category)
        assertTrue(descartes.isEmpty())
    }
}
