package com.fabrizio.recordatorios.ui.ajustes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fabrizio.recordatorios.BuildConfig
import com.fabrizio.recordatorios.data.preferencias.CriterioOrden
import com.fabrizio.recordatorios.data.preferencias.FiltroPrioridad
import com.fabrizio.recordatorios.data.preferencias.PreferenciasUsuario
import com.fabrizio.recordatorios.data.preferencias.TemaApp
import com.fabrizio.recordatorios.ui.components.IndicadorCarga
import com.fabrizio.recordatorios.ui.components.SelectorOpciones
import com.fabrizio.recordatorios.ui.theme.RecordatoriosTheme

@Composable
fun AjustesScreen(
    onVolver: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AjustesViewModel = viewModel(factory = AjustesViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    AjustesContenido(
        uiState = uiState,
        onVolver = onVolver,
        onTemaSeleccionado = viewModel::onTemaSeleccionado,
        onMostrarCompletadosChange = viewModel::onMostrarCompletadosChange,
        onOrdenSeleccionado = viewModel::onOrdenSeleccionado,
        onFiltroPrioridadSeleccionado = viewModel::onFiltroPrioridadSeleccionado,
        onSimularTemaInvalido = viewModel::onSimularTemaInvalido,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AjustesContenido(
    uiState: AjustesUiState,
    onVolver: () -> Unit,
    onTemaSeleccionado: (TemaApp) -> Unit,
    onMostrarCompletadosChange: (Boolean) -> Unit,
    onOrdenSeleccionado: (CriterioOrden) -> Unit,
    onFiltroPrioridadSeleccionado: (FiltroPrioridad) -> Unit,
    onSimularTemaInvalido: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { innerPadding ->
        when (uiState) {
            AjustesUiState.Cargando -> IndicadorCarga(
                mensaje = "Cargando ajustes…",
                modifier = Modifier.padding(innerPadding)
            )

            is AjustesUiState.Listo -> {
                val prefs = uiState.preferencias
                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    SelectorOpciones(
                        titulo = "Tema",
                        opciones = TemaApp.entries,
                        seleccionada = prefs.tema,
                        etiquetaDe = { it.etiqueta },
                        onSeleccion = onTemaSeleccionado,
                        error = null
                    )
                    HorizontalDivider()
                    FilaSwitch(
                        titulo = "Mostrar completados",
                        descripcion = "Incluir en el listado los recordatorios ya completados",
                        activado = prefs.mostrarCompletados,
                        onCambio = onMostrarCompletadosChange
                    )
                    HorizontalDivider()
                    SelectorOpciones(
                        titulo = "Ordenar por",
                        opciones = CriterioOrden.entries,
                        seleccionada = prefs.orden,
                        etiquetaDe = { it.etiqueta },
                        onSeleccion = onOrdenSeleccionado,
                        error = null
                    )
                    SelectorOpciones(
                        titulo = "Filtrar por prioridad",
                        opciones = FiltroPrioridad.entries,
                        seleccionada = prefs.filtroPrioridad,
                        etiquetaDe = { it.etiqueta },
                        onSeleccion = onFiltroPrioridadSeleccionado,
                        error = null
                    )
                    // Solo en debug: prueba del valor por defecto ante un dato inválido
                    if (BuildConfig.DEBUG) {
                        HorizontalDivider()
                        OutlinedButton(onClick = onSimularTemaInvalido) {
                            Text("Prueba: guardar tema inválido")
                        }
                    }
                }
            }
        }
    }
}

// Fila completa clickeable con un Switch (toggleable: accesible como una sola unidad)
@Composable
private fun FilaSwitch(
    titulo: String,
    descripcion: String,
    activado: Boolean,
    onCambio: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(value = activado, role = Role.Switch, onValueChange = onCambio),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.titleSmall)
            Text(
                descripcion,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // onCheckedChange = null: el click lo maneja la fila entera
        Switch(checked = activado, onCheckedChange = null)
    }
}

@Preview(showBackground = true)
@Composable
private fun AjustesPreview() {
    RecordatoriosTheme {
        AjustesContenido(
            uiState = AjustesUiState.Listo(PreferenciasUsuario()),
            onVolver = {},
            onTemaSeleccionado = {},
            onMostrarCompletadosChange = {},
            onOrdenSeleccionado = {},
            onFiltroPrioridadSeleccionado = {},
            onSimularTemaInvalido = {}
        )
    }
}
