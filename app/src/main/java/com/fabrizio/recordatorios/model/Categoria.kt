package com.fabrizio.recordatorios.model

sealed class Categoria(val etiqueta: String) {
    data object Estudio : Categoria("Estudio")
    data object Academica : Categoria("Académica")
    data object Personal : Categoria("Personal")
    data object Trabajo : Categoria("Trabajo")

    companion object {
        // Opciones válidas del formulario. Se usa un getter (y no una val inicializada)
        // para evitar problemas de orden de inicialización entre la sealed class y sus objetos.
        val opciones: List<Categoria>
            get() = listOf(Estudio, Academica, Personal, Trabajo)
    }
}
