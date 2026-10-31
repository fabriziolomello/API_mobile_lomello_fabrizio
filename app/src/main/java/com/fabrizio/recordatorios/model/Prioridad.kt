package com.fabrizio.recordatorios.model

// etiqueta: texto visible. codigo: valor estable para Room y el servidor.
sealed class Prioridad(val etiqueta: String, val nivel: Int, val codigo: String) {
    data object Alta : Prioridad("Alta", 3, "ALTA")
    data object Media : Prioridad("Media", 2, "MEDIA")
    data object Baja : Prioridad("Baja", 1, "BAJA")

    companion object {
        // Ordenadas de mayor a menor para mostrarlas así en el formulario
        val opciones: List<Prioridad>
            get() = listOf(Alta, Media, Baja)

        // Código desconocido -> valor seguro, sin crashear
        fun desdeCodigo(codigo: String?): Prioridad =
            opciones.firstOrNull { it.codigo == codigo } ?: Media
    }
}
