package com.fabrizio.recordatorios

import com.fabrizio.recordatorios.data.local.Converters
import com.fabrizio.recordatorios.data.local.SyncStatus
import com.fabrizio.recordatorios.model.Categoria
import com.fabrizio.recordatorios.model.Prioridad
import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun categoriaIdaYVuelta() {
        Categoria.opciones.forEach {
            assertEquals(it, converters.codigoACategoria(converters.categoriaACodigo(it)))
        }
    }

    @Test
    fun prioridadIdaYVuelta() {
        Prioridad.opciones.forEach {
            assertEquals(it, converters.codigoAPrioridad(converters.prioridadACodigo(it)))
        }
    }

    @Test
    fun codigosDesconocidosUsanValorSeguro() {
        assertEquals(Categoria.Personal, converters.codigoACategoria("INEXISTENTE"))
        assertEquals(Prioridad.Media, converters.codigoAPrioridad("INEXISTENTE"))
        assertEquals(SyncStatus.PENDING_UPDATE, converters.codigoASyncStatus("INEXISTENTE"))
    }
}
