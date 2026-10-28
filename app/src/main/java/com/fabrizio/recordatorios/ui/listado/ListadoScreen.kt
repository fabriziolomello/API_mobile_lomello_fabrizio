package com.fabrizio.recordatorios.ui.listado

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fabrizio.recordatorios.BuildConfig
import com.fabrizio.recordatorios.data.EscenarioListado
import com.fabrizio.recordatorios.data.recordatoriosSimulados
import com.fabrizio.recordatorios.ui.components.EstadoError
import com.fabrizio.recordatorios.ui.components.EstadoVacio
import com.fabrizio.recordatorios.ui.components.IndicadorCarga
import com.fabrizio.recordatorios.ui.components.TarjetaRecordatorio
import com.fabrizio.recordatorios.ui.theme.RecordatoriosTheme

// Id que no existe, para probar el estado "no encontrado" del detalle
private const val ID_INEXISTENTE = 999L

// Versión "con estado": obtiene el ViewModel y observa sus StateFlow.
@Composable
fun ListadoScreen(
    onCrearRecordatorio: () -> Unit,
    onRecordatorioClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ListadoViewModel = viewModel(factory = ListadoViewModel.Factory)
) {
    // collectAsStateWithLifecycle deja de observar cuando la app está en segundo plano
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val escenario by viewModel.escenario.collectAsStateWithLifecycle()
    val fallarGuardado by viewModel.fallarGuardado.collectAsStateWithLifecycle()

    ListadoContenido(
        uiState = uiState,
        onCrearRecordatorio = onCrearRecordatorio,
        onRecordatorioClick = onRecordatorioClick,
        onReintentar = viewModel::onReintentar,
        modifier = modifier,
        menuSimulacion = {
            // El menú de simulación solo aparece en builds de debug
            if (BuildConfig.DEBUG) {
                MenuSimulacion(
                    escenario = escenario,
                    fallarGuardado = fallarGuardado,
                    onEscenarioSeleccionado = viewModel::onEscenarioSeleccionado,
                    onFallarGuardadoChange = viewModel::onFallarGuardadoChange,
                    onAbrirIdInexistente = { onRecordatorioClick(ID_INEXISTENTE) }
                )
            }
        }
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
    modifier: Modifier = Modifier,
    menuSimulacion: @Composable () -> Unit = {}
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Mis recordatorios") },
                actions = { menuSimulacion() }
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

// Menú de la TopAppBar para forzar estados y sacar capturas
@Composable
private fun MenuSimulacion(
    escenario: EscenarioListado,
    fallarGuardado: Boolean,
    onEscenarioSeleccionado: (EscenarioListado) -> Unit,
    onFallarGuardadoChange: (Boolean) -> Unit,
    onAbrirIdInexistente: () -> Unit
) {
    // Estado puramente visual (menú abierto/cerrado): puede vivir en la UI
    var expandido by remember { mutableStateOf(false) }

    IconButton(onClick = { expandido = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = "Opciones de simulación")
    }
    DropdownMenu(expanded = expandido, onDismissRequest = { expandido = false }) {
        Text("Simular listado", modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
        EscenarioListado.entries.forEach { opcion ->
            DropdownMenuItem(
                text = { Text(opcion.etiqueta) },
                leadingIcon = { RadioButton(selected = opcion == escenario, onClick = null) },
                onClick = {
                    onEscenarioSeleccionado(opcion)
                    expandido = false
                }
            )
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("Fallar al guardar") },
            leadingIcon = { Checkbox(checked = fallarGuardado, onCheckedChange = null) },
            onClick = { onFallarGuardadoChange(!fallarGuardado) }
        )
        DropdownMenuItem(
            text = { Text("Abrir detalle inexistente") },
            onClick = {
                expandido = false
                onAbrirIdInexistente()
            }
        )
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
            onReintentar = {}
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
            onReintentar = {}
        )
    }
}
