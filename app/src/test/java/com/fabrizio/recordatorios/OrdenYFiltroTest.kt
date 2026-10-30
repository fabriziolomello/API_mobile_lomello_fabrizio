package com.fabrizio.recordatorios

import com.fabrizio.recordatorios.data.preferencias.CriterioOrden
import com.fabrizio.recordatorios.data.preferencias.FiltroPrioridad
import com.fabrizio.recordatorios.data.preferencias.PreferenciasUsuario
import com.fabrizio.recordatorios.data.recordatoriosSimulados
import com.fabrizio.recordatorios.model.Estado
import com.fabrizio.recordatorios.model.Prioridad
import com.fabrizio.recordatorios.ui.listado.aplicarPreferencias
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrdenYFiltroTest {

    @Test
    fun ocultaCompletadosCuandoLaPreferenciaEstaDesactivada() {
        val resultado = recordatoriosSimulados.aplicarPreferencias(
            PreferenciasUsuario(mostrarCompletados = false)
        )
        assertTrue(resultado.none { it.estado == Estado.Completado })
    }

    @Test
    fun filtraPorPrioridad() {
        val resultado = recordatoriosSimulados.aplicarPreferencias(
            PreferenciasUsuario(filtroPrioridad = FiltroPrioridad.ALTA)
        )
        assertTrue(resultado.isNotEmpty())
        assertTrue(resultado.all { it.prioridad == Prioridad.Alta })
    }

    @Test
    fun ordenaPorTituloSinDistinguirMayusculas() {
        val resultado = recordatoriosSimulados.aplicarPreferencias(
            PreferenciasUsuario(orden = CriterioOrden.TITULO)
        )
        assertEquals(resultado.map { it.titulo.lowercase() }.sorted(), resultado.map { it.titulo.lowercase() })
    }

    @Test
    fun ordenaPorPrioridadDeMayorAMenor() {
        val niveles = recordatoriosSimulados
            .aplicarPreferencias(PreferenciasUsuario(orden = CriterioOrden.PRIORIDAD))
            .map { it.prioridad.nivel }
        assertEquals(niveles.sortedDescending(), niveles)
    }
}
