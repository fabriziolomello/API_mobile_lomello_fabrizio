package com.fabrizio.recordatorios.model

// Reglas de validación del formulario de creación.
// Cada función devuelve null si el valor es válido, o el mensaje de error a mostrar.
// Son funciones puras (sin Android), así el ViewModel las usa y se pueden testear fácil.
object ValidacionRecordatorio {

    const val TITULO_MIN_CARACTERES = 4

    // Mismo límite que firestore.rules (title.size() <= 200): un título más largo se
    // guardaría en Room pero Firestore lo rechazaría y la fila quedaría en error.
    const val TITULO_MAX_CARACTERES = 200
    const val DESCRIPCION_MAX_CARACTERES = 200

    // Se mide después de trim(): es lo que se guarda y se sincroniza
    fun validarTitulo(titulo: String): String? {
        val largo = titulo.trim().length
        return when {
            largo < TITULO_MIN_CARACTERES ->
                "El título debe tener al menos $TITULO_MIN_CARACTERES caracteres"
            largo > TITULO_MAX_CARACTERES ->
                "El título no puede superar los $TITULO_MAX_CARACTERES caracteres"
            else -> null
        }
    }

    fun validarDescripcion(descripcion: String): String? =
        if (descripcion.length > DESCRIPCION_MAX_CARACTERES) {
            "La descripción no puede superar los $DESCRIPCION_MAX_CARACTERES caracteres"
        } else {
            null
        }

    // La categoría debe estar elegida y pertenecer a las opciones definidas
    fun validarCategoria(categoria: Categoria?): String? =
        if (categoria == null || categoria !in Categoria.opciones) {
            "Elegí una categoría"
        } else {
            null
        }

    fun validarPrioridad(prioridad: Prioridad?): String? =
        if (prioridad == null) "Elegí una prioridad" else null
}
