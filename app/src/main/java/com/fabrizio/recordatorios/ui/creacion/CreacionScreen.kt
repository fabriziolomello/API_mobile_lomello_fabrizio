package com.fabrizio.recordatorios.ui.creacion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fabrizio.recordatorios.model.Categoria
import com.fabrizio.recordatorios.model.Prioridad
import com.fabrizio.recordatorios.model.ValidacionRecordatorio
import com.fabrizio.recordatorios.ui.components.CampoTexto
import com.fabrizio.recordatorios.ui.components.MensajeError
import com.fabrizio.recordatorios.ui.components.SelectorOpciones
import com.fabrizio.recordatorios.ui.theme.RecordatoriosTheme

@Composable
fun CreacionScreen(
    onGuardar: () -> Unit,
    onCancelar: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CreacionViewModel = viewModel(factory = CreacionViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Cuando el guardado termina bien: primero se consume el éxito y después se navega.
    // LaunchedEffect se re-ejecuta solo cuando cambia `envio`, no en cada recomposición.
    LaunchedEffect(uiState.envio) {
        if (uiState.envio == EstadoEnvio.Exito) {
            viewModel.onExitoConsumido()
            onGuardar()
        }
    }

    CreacionContenido(
        uiState = uiState,
        onTituloChange = viewModel::onTituloChange,
        onDescripcionChange = viewModel::onDescripcionChange,
        onCategoriaSeleccionada = viewModel::onCategoriaSeleccionada,
        onPrioridadSeleccionada = viewModel::onPrioridadSeleccionada,
        onGuardar = viewModel::onGuardar,
        onCancelar = onCancelar,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreacionContenido(
    uiState: CreacionUiState,
    onTituloChange: (String) -> Unit,
    onDescripcionChange: (String) -> Unit,
    onCategoriaSeleccionada: (Categoria) -> Unit,
    onPrioridadSeleccionada: (Prioridad) -> Unit,
    onGuardar: () -> Unit,
    onCancelar: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Mientras se guarda, los campos quedan deshabilitados
    val habilitado = !uiState.enviando

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Nuevo recordatorio") },
                navigationIcon = {
                    IconButton(onClick = onCancelar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancelar y volver")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CampoTexto(
                valor = uiState.titulo,
                onValorChange = onTituloChange,
                etiqueta = "Título *",
                error = uiState.errorTitulo,
                habilitado = habilitado
            )
            CampoTexto(
                valor = uiState.descripcion,
                onValorChange = onDescripcionChange,
                etiqueta = "Descripción (opcional)",
                error = uiState.errorDescripcion,
                singleLine = false,
                maxCaracteres = ValidacionRecordatorio.DESCRIPCION_MAX_CARACTERES,
                habilitado = habilitado
            )
            SelectorOpciones(
                titulo = "Categoría *",
                opciones = Categoria.opciones,
                seleccionada = uiState.categoria,
                etiquetaDe = { it.etiqueta },
                onSeleccion = onCategoriaSeleccionada,
                error = uiState.errorCategoria,
                habilitado = habilitado
            )
            SelectorOpciones(
                titulo = "Prioridad *",
                opciones = Prioridad.opciones,
                seleccionada = uiState.prioridad,
                etiquetaDe = { it.etiqueta },
                onSeleccion = onPrioridadSeleccionada,
                error = uiState.errorPrioridad,
                habilitado = habilitado
            )

            Text(
                text = "* Campos obligatorios",
                style = MaterialTheme.typography.bodySmall
            )

            // Error general de guardado (con liveRegion para que TalkBack lo anuncie)
            val envio = uiState.envio
            if (envio is EstadoEnvio.Error) {
                MensajeError(
                    mensaje = envio.mensaje,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
            ) {
                OutlinedButton(onClick = onCancelar, enabled = habilitado) {
                    Text("Cancelar")
                }
                Button(onClick = onGuardar, enabled = uiState.puedeGuardar) {
                    if (uiState.enviando) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Guardando…")
                    } else {
                        Text("Guardar")
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CreacionConErroresPreview() {
    RecordatoriosTheme {
        CreacionContenido(
            uiState = CreacionUiState(
                titulo = "Ab",
                errorTitulo = ValidacionRecordatorio.validarTitulo("Ab"),
                envio = EstadoEnvio.Error("No se pudo guardar el recordatorio. Intentá de nuevo.")
            ),
            onTituloChange = {},
            onDescripcionChange = {},
            onCategoriaSeleccionada = {},
            onPrioridadSeleccionada = {},
            onGuardar = {},
            onCancelar = {}
        )
    }
}
