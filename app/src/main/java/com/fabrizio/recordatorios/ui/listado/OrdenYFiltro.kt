package com.fabrizio.recordatorios.ui.listado

import com.fabrizio.recordatorios.data.preferencias.CriterioOrden
import com.fabrizio.recordatorios.data.preferencias.PreferenciasUsuario
import com.fabrizio.recordatorios.model.Estado
import com.fabrizio.recordatorios.model.Recordatorio

// Aplica las preferencias del usuario a la lista: primero filtra, después ordena.
// Función pura (sin Android): fácil de testear y no modifica la lista original.
fun List<Recordatorio>.aplicarPreferencias(prefs: PreferenciasUsuario): List<Recordatorio> {
    val prioridadElegida = prefs.filtroPrioridad.prioridad
    val filtrados = filter { recordatorio ->
        (prefs.mostrarCompletados || recordatorio.estado != Estado.Completado) &&
            (prioridadElegida == null || recordatorio.prioridad == prioridadElegida)
    }
    return when (prefs.orden) {
        // El id crece con cada alta: mayor id = más reciente
        CriterioOrden.CREACION -> filtrados.sortedByDescending { it.id }
        CriterioOrden.PRIORIDAD -> filtrados.sortedWith(
            compareByDescending<Recordatorio> { it.prioridad.nivel }.thenByDescending { it.id }
        )
        CriterioOrden.TITULO -> filtrados.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.titulo })
    }
}
