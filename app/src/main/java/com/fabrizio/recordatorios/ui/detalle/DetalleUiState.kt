package com.fabrizio.recordatorios.ui.detalle

import com.fabrizio.recordatorios.model.Recordatorio

sealed interface DetalleUiState {
    data object Cargando : DetalleUiState
    data class Encontrado(val recordatorio: Recordatorio) : DetalleUiState
    data object NoEncontrado : DetalleUiState
}
