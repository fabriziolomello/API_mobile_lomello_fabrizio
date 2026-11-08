package com.fabrizio.recordatorios.navigation

// Rutas de la app en un solo lugar, para no repetir strings sueltos
object Rutas {
    // Pantalla de carga mientras se sabe si hay sesión (destino inicial del NavHost)
    const val INICIO = "inicio"
    const val LOGIN = "login"
    const val REGISTRO = "registro"

    const val LISTADO = "listado"
    const val CREAR = "crear"
    const val AJUSTES = "ajustes"

    const val ARG_ID = "id"
    const val DETALLE = "detalle/{$ARG_ID}"
    const val EDITAR = "editar/{$ARG_ID}"

    // Construye la ruta concreta: al detalle viaja solo el id
    fun detalle(id: Long): String = "detalle/$id"

    fun editar(id: Long): String = "editar/$id"

    // Rutas a las que se puede estar sin sesión
    val SIN_SESION = setOf(LOGIN, REGISTRO)
}
