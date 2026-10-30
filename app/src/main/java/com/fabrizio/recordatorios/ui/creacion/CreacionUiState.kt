package com.fabrizio.recordatorios.ui.creacion

import com.fabrizio.recordatorios.model.Categoria
import com.fabrizio.recordatorios.model.Prioridad

// Estado del envío del formulario (sealed: solo puede ser uno a la vez)
sealed interface EstadoEnvio {
    data object Inactivo : EstadoEnvio
    data object Enviando : EstadoEnvio
    data object Exito : EstadoEnvio // se consume una sola vez para navegar
    data class Error(val mensaje: String) : EstadoEnvio
}

// Estado inmutable del formulario de creación/edición. Cada cambio genera una copia nueva (copy).
data class CreacionUiState(
    // true si se abrió desde "Editar" (mismo formulario, distinto título y acción final)
    val modoEdicion: Boolean = false,
    // true mientras se leen los datos del recordatorio a editar
    val cargando: Boolean = false,
    // Valores de los campos
    val titulo: String = "",
    val descripcion: String = "",
    val categoria: Categoria? = null,
    val prioridad: Prioridad? = null,
    // Opcional: epoch millis a medianoche UTC (lo que devuelve el DatePicker)
    val fechaLimite: Long? = null,
    // Mensajes de validación (null = sin error visible).
    // Solo se muestran después de que el usuario tocó el campo.
    val errorTitulo: String? = null,
    val errorDescripcion: String? = null,
    val errorCategoria: String? = null,
    val errorPrioridad: String? = null,
    // true si todos los valores cumplen las reglas (lo calcula el ViewModel)
    val formularioValido: Boolean = false,
    val envio: EstadoEnvio = EstadoEnvio.Inactivo
) {
    val enviando: Boolean get() = envio == EstadoEnvio.Enviando

    // Guardar se deshabilita si el formulario es inválido o hay un envío en curso
    val puedeGuardar: Boolean get() = formularioValido && !enviando && !cargando
}
