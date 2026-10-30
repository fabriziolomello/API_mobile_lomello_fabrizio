package com.fabrizio.recordatorios.ui.listado

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fabrizio.recordatorios.data.recordatoriosSimulados
import com.fabrizio.recordatorios.ui.components.EstadoError
import com.fabrizio.recordatorios.ui.components.EstadoVacio
import com.fabrizio.recordatorios.ui.components.IndicadorCarga
import com.fabrizio.recordatorios.ui.components.TarjetaRecordatorio
import com.fabrizio.recordatorios.ui.theme.RecordatoriosTheme

// Versión "con estado": obtiene el ViewModel y observa sus StateFlow.
@Composable
fun ListadoScreen(
    onCrearRecordatorio: () -> Unit,
    onRecordatorioClick: (Long) -> Unit,
    onAbrirAjustes: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ListadoViewModel = viewModel(factory = ListadoViewModel.Factory)
) {
    // collectAsStateWithLifecycle deja de observar cuando la app está en segundo plano
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ListadoContenido(
        uiState = uiState,
        onCrearRecordatorio = onCrearRecordatorio,
        onRecordatorioClick = onRecordatorioClick,
        onReintentar = viewModel::onReintentar,
        onAbrirAjustes = onAbrirAjustes,
        modifier = modifier
    )
}

// Versión "sin estado": solo dibuja lo que recibe. Se puede previsualizar sin ViewModel.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListadoContenido(
    uiState: ListadoUiState,
    onCrearRecordatorio: () -> Unit,
    onRecordatorioClick: (Long) -> Unit,
    onReintentar: () -> Unit,
    onAbrirAjustes: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Mis recordatorios") },
                actions = {
                    IconButton(onClick = onAbrirAjustes) {
                        Icon(Icons.Filled.Settings, contentDescription = "Ajustes")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCrearRecordatorio) {
                Icon(Icons.Filled.Add, contentDescription = "Crear recordatorio")
            }
        }
    ) { innerPadding ->
        // Se aplica el innerPadding para no quedar debajo de la TopAppBar
        val contenidoModifier = Modifier.padding(innerPadding)

        // when exhaustivo: si se agrega un estado nuevo, el compilador obliga a manejarlo
        when (uiState) {
            ListadoUiState.Cargando -> IndicadorCarga(
                mensaje = "Cargando recordatorios…",
                modifier = contenidoModifier
            )

            ListadoUiState.Vacio -> EstadoVacio(
                mensaje = "Todavía no tenés recordatorios.",
                textoAccion = "Crear recordatorio",
                onAccion = onCrearRecordatorio,
                modifier = contenidoModifier
            )

            // Los datos existen pero las preferencias los ocultan: se ofrece ir a Ajustes
            ListadoUiState.SinCoincidencias -> EstadoVacio(
                mensaje = "Ningún recordatorio coincide con los filtros de Ajustes.",
                textoAccion = "Revisar ajustes",
                onAccion = onAbrirAjustes,
                modifier = contenidoModifier
            )

            is ListadoUiState.Error -> EstadoError(
                mensaje = uiState.mensaje,
                onReintentar = onReintentar,
                modifier = contenidoModifier
            )

            is ListadoUiState.Datos -> LazyColumn(
                modifier = contenidoModifier.fillMaxSize(),
                // Espacio extra abajo para que el FAB no tape la última tarjeta
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.recordatorios, key = { it.id }) { recordatorio ->
                    TarjetaRecordatorio(
                        recordatorio = recordatorio,
                        onClick = { onRecordatorioClick(recordatorio.id) }
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ListadoDatosPreview() {
    RecordatoriosTheme {
        ListadoContenido(
            uiState = ListadoUiState.Datos(recordatoriosSimulados),
            onCrearRecordatorio = {},
            onRecordatorioClick = {},
            onReintentar = {},
            onAbrirAjustes = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ListadoVacioPreview() {
    RecordatoriosTheme {
        ListadoContenido(
            uiState = ListadoUiState.Vacio,
            onCrearRecordatorio = {},
            onRecordatorioClick = {},
            onReintentar = {},
            onAbrirAjustes = {}
        )
    }
}
