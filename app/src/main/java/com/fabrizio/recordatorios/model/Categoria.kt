package com.fabrizio.recordatorios.model

// etiqueta: texto visible. codigo: valor estable que se guarda en Room y viaja al servidor
// (no depende del idioma ni de cambios de texto en la UI).
sealed class Categoria(val etiqueta: String, val codigo: String) {
    data object Estudio : Categoria("Estudio", "ESTUDIO")
    data object Academica : Categoria("Académica", "ACADEMICA")
    data object Personal : Categoria("Personal", "PERSONAL")
    data object Trabajo : Categoria("Trabajo", "TRABAJO")

    companion object {
        // Opciones válidas del formulario. Se usa un getter (y no una val inicializada)
        // para evitar problemas de orden de inicialización entre la sealed class y sus objetos.
        val opciones: List<Categoria>
            get() = listOf(Estudio, Academica, Personal, Trabajo)

        // Código desconocido (dato viejo o del servidor) -> valor seguro, sin crashear
        fun desdeCodigo(codigo: String?): Categoria =
            opciones.firstOrNull { it.codigo == codigo } ?: Personal
    }
}
