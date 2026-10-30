package com.fabrizio.recordatorios.navigation

// Rutas de la app en un solo lugar, para no repetir strings sueltos
object Rutas {
    const val LISTADO = "listado"
    const val CREAR = "crear"
    const val AJUSTES = "ajustes"

    const val ARG_ID = "id"
    const val DETALLE = "detalle/{$ARG_ID}"
    const val EDITAR = "editar/{$ARG_ID}"

    // Construye la ruta concreta: al detalle viaja solo el id
    fun detalle(id: Long): String = "detalle/$id"

    fun editar(id: Long): String = "editar/$id"
}
