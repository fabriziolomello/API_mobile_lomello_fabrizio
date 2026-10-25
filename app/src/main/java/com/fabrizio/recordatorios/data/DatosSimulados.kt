package com.fabrizio.recordatorios.data

import androidx.compose.ui.graphics.Color
import com.fabrizio.recordatorios.model.Categoria
import com.fabrizio.recordatorios.model.Estado
import com.fabrizio.recordatorios.model.Prioridad
import com.fabrizio.recordatorios.model.Recordatorio

// LIST: recordatorios simulados, con orden fijo
val recordatoriosSimulados: List<Recordatorio> = listOf(
    Recordatorio(
        id = 1,
        titulo = "Estudiar Kotlin",
        categoria = Categoria.Estudio,
        prioridad = Prioridad.Alta,
        estado = Estado.Pendiente,
        descripcion = "Repasar data classes y sealed classes"
    ),
    Recordatorio(
        id = 2,
        titulo = "Entregar actividad",
        categoria = Categoria.Academica,
        prioridad = Prioridad.Media,
        estado = Estado.Pendiente,
        fechaLimite = "28/10"
    ),
    Recordatorio(
        id = 3,
        titulo = "Revisar apuntes",
        categoria = Categoria.Personal,
        prioridad = Prioridad.Baja,
        estado = Estado.Completado
    ),
    Recordatorio(
        id = 4,
        titulo = "Subir proyecto a GitHub",
        categoria = Categoria.Academica,
        prioridad = Prioridad.Alta,
        estado = Estado.Pendiente,
        descripcion = "Crear el repositorio y compartir el enlace"
    ),
    Recordatorio(
        id = 5,
        titulo = "Enviar CV a una empresa",
        categoria = Categoria.Trabajo,
        prioridad = Prioridad.Alta,
        estado = Estado.Pendiente,
        fechaLimite = "30/10"
    ),
    Recordatorio(
        id = 6,
        titulo = "Ordenar escritorio",
        categoria = Categoria.Personal,
        prioridad = Prioridad.Baja,
        estado = Estado.Completado
    ),
    Recordatorio(
        id = 7,
        titulo = "Ver video de Jetpack Compose",
        categoria = Categoria.Estudio,
        prioridad = Prioridad.Media,
        estado = Estado.Completado
    )
)

// SET: categorías sin repetir, derivadas de los recordatorios
val categoriasUsadas: Set<Categoria> =
    recordatoriosSimulados.map { it.categoria }.toSet()

// MAP: cada categoría asociada a un color
val colorPorCategoria: Map<Categoria, Color> = mapOf(
    Categoria.Estudio to Color(0xFF1E88E5),
    Categoria.Academica to Color(0xFF8E24AA),
    Categoria.Personal to Color(0xFF43A047),
    Categoria.Trabajo to Color(0xFFFB8C00)
)

// FILTROS: devuelven una lista nueva, sin modificar la original
fun List<Recordatorio>.filtrarPorEstado(estado: Estado): List<Recordatorio> =
    filter { it.estado == estado }

fun List<Recordatorio>.filtrarPorPrioridad(prioridad: Prioridad): List<Recordatorio> =
    filter { it.prioridad == prioridad }