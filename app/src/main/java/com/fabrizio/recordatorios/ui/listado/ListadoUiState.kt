package com.fabrizio.recordatorios.ui.listado

import com.fabrizio.recordatorios.data.sync.EstadoSincronizacion
import com.fabrizio.recordatorios.model.Recordatorio

// Estados posibles del listado. Al ser sealed, el `when` de la UI
// tiene que cubrir todos los casos o no compila.
sealed interface ListadoUiState {
    data object Cargando : ListadoUiState
    data object Vacio : ListadoUiState
    // Hay recordatorios, pero las preferencias (filtros) los ocultan a todos
    data object SinCoincidencias : ListadoUiState
    data class Datos(val recordatorios: List<Recordatorio>) : ListadoUiState
    data class Error(val mensaje: String) : ListadoUiState
}

// Estado de la sincronización que muestra el listado
data class SincronizacionUi(
    val sincronizando: Boolean = false,
    val conectado: Boolean = true,
    val pendientes: Int = 0,
    val ultimaSincronizacion: String? = null,
    // Último error (red, servidor o rechazo); se muestra en un Snackbar con "Reintentar"
    val error: EstadoSincronizacion.Error? = null
)

// Pasos del cierre de sesión (cada uno se muestra como un diálogo)
sealed interface CierreSesionUi {
    data object Inactivo : CierreSesionUi
    // "¿Cerrar sesión?" con Cancelar / Cerrar sesión
    data object Confirmando : CierreSesionUi
    // Intentando subir lo pendiente antes de salir
    data object Sincronizando : CierreSesionUi
    // El sync no pudo subir todo: se avisa cuántos se perderían
    data class AvisoPendientes(val cantidad: Int) : CierreSesionUi
    // Borrando datos locales y cerrando la sesión
    data object Cerrando : CierreSesionUi
    data object Error : CierreSesionUi
}
