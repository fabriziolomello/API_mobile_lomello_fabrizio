package com.fabrizio.recordatorios.ui.listado

import com.fabrizio.recordatorios.model.Recordatorio

// Estados posibles del listado. Al ser sealed, el `when` de la UI
// tiene que cubrir los cuatro casos o no compila.
sealed interface ListadoUiState {
    data object Cargando : ListadoUiState
    data object Vacio : ListadoUiState
    data class Datos(val recordatorios: List<Recordatorio>) : ListadoUiState
    data class Error(val mensaje: String) : ListadoUiState
}
