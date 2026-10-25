package com.fabrizio.recordatorios.model

sealed class Categoria(val etiqueta: String) {
    data object Estudio : Categoria("Estudio")
    data object Academica : Categoria("Académica")
    data object Personal : Categoria("Personal")
    data object Trabajo : Categoria("Trabajo")
}