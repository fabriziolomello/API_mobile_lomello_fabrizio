package com.fabrizio.recordatorios.model

sealed class Prioridad(val etiqueta: String, val nivel: Int) {
    data object Alta : Prioridad("Alta", 3)
    data object Media : Prioridad("Media", 2)
    data object Baja : Prioridad("Baja", 1)

    companion object {
        // Ordenadas de mayor a menor para mostrarlas así en el formulario
        val opciones: List<Prioridad>
            get() = listOf(Alta, Media, Baja)
    }
}
