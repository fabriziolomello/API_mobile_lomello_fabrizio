package com.fabrizio.recordatorios.ui.detalle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fabrizio.recordatorios.data.recordatoriosSimulados
import com.fabrizio.recordatorios.model.Estado
import com.fabrizio.recordatorios.model.FormatoFecha
import com.fabrizio.recordatorios.model.Recordatorio
import com.fabrizio.recordatorios.ui.components.EstadoError
import com.fabrizio.recordatorios.ui.components.IndicadorCarga
import com.fabrizio.recordatorios.ui.theme.RecordatoriosTheme

@Composable
fun DetalleScreen(
    onVolver: () -> Unit,
    onEditar: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DetalleViewModel = viewModel(factory = DetalleViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mensaje by viewModel.mensaje.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Después de eliminar se vuelve al listado (una sola vez)
    LaunchedEffect(uiState) {
        if (uiState == DetalleUiState.Eliminado) onVolver()
    }

    // Los errores se muestran en un Snackbar y se marcan como consumidos
    LaunchedEffect(mensaje) {
        mensaje?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onMensajeMostrado()
        }
    }

    DetalleContenido(
        uiState = uiState,
        onVolver = onVolver,
        onEditar = onEditar,
        onCompletadoChange = viewModel::onCompletadoChange,
        onEliminarConfirmado = viewModel::onEliminarConfirmado,
        snackbarHostState = snackbarHostState,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetalleContenido(
    uiState: DetalleUiState,
    onVolver: () -> Unit,
    onEditar: (Long) -> Unit,
    onCompletadoChange: (Boolean) -> Unit,
    onEliminarConfirmado: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    // Diálogo de confirmación: estado visual, sobrevive a la rotación
    var confirmarEliminar by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Detalle") },
                navigationIcon = {
                    IconButton(onClick = onVolver) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    // Las acciones solo tienen sentido si el recordatorio existe
                    if (uiState is DetalleUiState.Encontrado) {
                        IconButton(onClick = { onEditar(uiState.recordatorio.id) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Editar")
                        }
                        IconButton(onClick = { confirmarEliminar = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Eliminar")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        val contenidoModifier = Modifier.padding(innerPadding)

        when (uiState) {
            // Eliminado: se muestra la carga un instante mientras se vuelve atrás
            DetalleUiState.Cargando, DetalleUiState.Eliminado -> IndicadorCarga(
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
                onCompletadoChange = onCompletadoChange,
                modifier = contenidoModifier
            )
        }
    }

    if (confirmarEliminar) {
        AlertDialog(
            onDismissRequest = { confirmarEliminar = false },
            title = { Text("¿Eliminar recordatorio?") },
            text = { Text("Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmarEliminar = false
                    onEliminarConfirmado()
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmarEliminar = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun DetalleRecordatorio(
    recordatorio: Recordatorio,
    onCompletadoChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val estaCompletado = recordatorio.estado == Estado.Completado

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
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
                FilaDato("Fecha límite", recordatorio.fechaLimite?.let(FormatoFecha::formatear) ?: "Sin fecha límite")
                FilaDato("Descripción", recordatorio.descripcion ?: "Sin descripción")
            }
        }

        // Cambio de estado: se guarda en Room al instante, haya o no conexión
        if (estaCompletado) {
            OutlinedButton(onClick = { onCompletadoChange(false) }, modifier = Modifier.fillMaxWidth()) {
                Text("Marcar como pendiente")
            }
        } else {
            Button(onClick = { onCompletadoChange(true) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Marcar como completado")
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
            onVolver = {},
            onEditar = {},
            onCompletadoChange = {},
            onEliminarConfirmado = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DetalleNoEncontradoPreview() {
    RecordatoriosTheme {
        DetalleContenido(
            uiState = DetalleUiState.NoEncontrado,
            onVolver = {},
            onEditar = {},
            onCompletadoChange = {},
            onEliminarConfirmado = {}
        )
    }
}
