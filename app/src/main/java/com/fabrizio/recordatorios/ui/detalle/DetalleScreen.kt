package com.fabrizio.recordatorios.ui.detalle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fabrizio.recordatorios.data.recordatoriosSimulados
import com.fabrizio.recordatorios.model.Recordatorio
import com.fabrizio.recordatorios.ui.components.EstadoError
import com.fabrizio.recordatorios.ui.components.IndicadorCarga
import com.fabrizio.recordatorios.ui.theme.RecordatoriosTheme

@Composable
fun DetalleScreen(
    onVolver: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DetalleViewModel = viewModel(factory = DetalleViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    DetalleContenido(uiState = uiState, onVolver = onVolver, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetalleContenido(
    uiState: DetalleUiState,
    onVolver: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Detalle") },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { innerPadding ->
        val contenidoModifier = Modifier.padding(innerPadding)

        when (uiState) {
            DetalleUiState.Cargando -> IndicadorCarga(
                mensaje = "Cargando recordatorio…",
                modifier = contenidoModifier
            )

            // Se reutiliza el componente de error, con "Volver" como acción
            DetalleUiState.NoEncontrado -> EstadoError(
                mensaje = "No encontramos este recordatorio. Puede que se haya eliminado o que el enlace sea incorrecto.",
                onReintentar = onVolver,
                textoAccion = "Volver al listado",
                modifier = contenidoModifier
            )

            is DetalleUiState.Encontrado -> DetalleRecordatorio(
                recordatorio = uiState.recordatorio,
                modifier = contenidoModifier
            )
        }
    }
}

@Composable
private fun DetalleRecordatorio(
    recordatorio: Recordatorio,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = recordatorio.titulo,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                HorizontalDivider()
                FilaDato("Categoría", recordatorio.categoria.etiqueta)
                FilaDato("Prioridad", recordatorio.prioridad.etiqueta)
                FilaDato("Estado", recordatorio.estado.etiqueta)
                FilaDato("Fecha límite", recordatorio.fechaLimite ?: "Sin fecha límite")
                FilaDato("Descripción", recordatorio.descripcion ?: "Sin descripción")
            }
        }
    }
}

// Etiqueta + valor; mergeDescendants hace que TalkBack los lea juntos
@Composable
private fun FilaDato(etiqueta: String, valor: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.semantics(mergeDescendants = true) {}) {
        Text(text = etiqueta, style = MaterialTheme.typography.labelMedium)
        Text(text = valor, style = MaterialTheme.typography.bodyLarge)
    }
}

@Preview(showBackground = true)
@Composable
private fun DetallePreview() {
    RecordatoriosTheme {
        DetalleContenido(
            uiState = DetalleUiState.Encontrado(recordatoriosSimulados.first()),
            onVolver = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DetalleNoEncontradoPreview() {
    RecordatoriosTheme {
        DetalleContenido(uiState = DetalleUiState.NoEncontrado, onVolver = {})
    }
}
