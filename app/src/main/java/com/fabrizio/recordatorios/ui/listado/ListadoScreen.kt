package com.fabrizio.recordatorios.ui.listado

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.fabrizio.recordatorios.R
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fabrizio.recordatorios.BuildConfig
import com.fabrizio.recordatorios.data.recordatoriosSimulados
import com.fabrizio.recordatorios.data.remote.FallaSimulada
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
    val sincronizacion by viewModel.sincronizacion.collectAsStateWithLifecycle()
    val fallaSimulada by viewModel.fallaSimulada.collectAsStateWithLifecycle()
    val cierreSesion by viewModel.cierreSesionUi.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Error de sincronización: Snackbar con "Reintentar". Los datos locales no se tocan.
    // La clave es el error (incluye su momento), así cada error nuevo se muestra una vez.
    LaunchedEffect(sincronizacion.error) {
        val error = sincronizacion.error ?: return@LaunchedEffect
        val resultado = snackbarHostState.showSnackbar(
            message = error.mensaje,
            actionLabel = "Reintentar",
            duration = SnackbarDuration.Long
        )
        if (resultado == SnackbarResult.ActionPerformed) viewModel.onReintentarSincronizacion()
    }

    ListadoContenido(
        uiState = uiState,
        sincronizacion = sincronizacion,
        onCrearRecordatorio = onCrearRecordatorio,
        onRecordatorioClick = onRecordatorioClick,
        onReintentar = viewModel::onReintentar,
        onSincronizar = viewModel::onSincronizar,
        onAbrirAjustes = onAbrirAjustes,
        onCerrarSesion = viewModel::onCerrarSesionClick,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
        menuDebug = {
            // El simulador de fallas solo existe en builds de debug
            if (BuildConfig.DEBUG) {
                MenuSimuladorFallas(
                    seleccionada = fallaSimulada,
                    onSeleccion = viewModel::onFallaSimuladaSeleccionada
                )
            }
        }
    )

    DialogosCierreSesion(
        estado = cierreSesion,
        onConfirmar = viewModel::onConfirmarCierreSesion,
        onConfirmarConPendientes = viewModel::onConfirmarCierreConPendientes,
        onCancelar = viewModel::onCancelarCierreSesion
    )
}

// Versión "sin estado": solo dibuja lo que recibe. Se puede previsualizar sin ViewModel.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListadoContenido(
    uiState: ListadoUiState,
    sincronizacion: SincronizacionUi,
    onCrearRecordatorio: () -> Unit,
    onRecordatorioClick: (Long) -> Unit,
    onReintentar: () -> Unit,
    onSincronizar: () -> Unit,
    onAbrirAjustes: () -> Unit,
    onCerrarSesion: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    menuDebug: @Composable () -> Unit = {}
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Mis recordatorios") },
                actions = {
                    // Sync manual accesible (además del pull-to-refresh)
                    IconButton(onClick = onSincronizar, enabled = !sincronizacion.sincronizando) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Sincronizar")
                    }
                    IconButton(onClick = onAbrirAjustes) {
                        Icon(Icons.Filled.Settings, contentDescription = "Ajustes")
                    }
                    IconButton(onClick = onCerrarSesion) {
                        Icon(
                            Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = stringResource(R.string.cerrar_sesion)
                        )
                    }
                    menuDebug()
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCrearRecordatorio) {
                Icon(Icons.Filled.Add, contentDescription = "Crear recordatorio")
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            BarraSincronizacion(sincronizacion)

            // Deslizar hacia abajo dispara un sync manual
            PullToRefreshBox(
                isRefreshing = sincronizacion.sincronizando,
                onRefresh = onSincronizar,
                modifier = Modifier.fillMaxSize()
            ) {
                // when exhaustivo: si se agrega un estado nuevo, el compilador obliga a manejarlo
                when (uiState) {
                    ListadoUiState.Cargando -> IndicadorCarga(mensaje = "Cargando recordatorios…")

                    ListadoUiState.Vacio -> EstadoVacio(
                        mensaje = "Todavía no tenés recordatorios.",
                        textoAccion = "Crear recordatorio",
                        onAccion = onCrearRecordatorio
                    )

                    // Los datos existen pero las preferencias los ocultan: se ofrece ir a Ajustes
                    ListadoUiState.SinCoincidencias -> EstadoVacio(
                        mensaje = "Ningún recordatorio coincide con los filtros de Ajustes.",
                        textoAccion = "Revisar ajustes",
                        onAccion = onAbrirAjustes
                    )

                    is ListadoUiState.Error -> EstadoError(
                        mensaje = uiState.mensaje,
                        onReintentar = onReintentar
                    )

                    is ListadoUiState.Datos -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
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
    }
}

// Franja de estado: conexión, cambios pendientes y último sync.
// La app sigue funcionando sin red; esto solo informa.
@Composable
private fun BarraSincronizacion(sincronizacion: SincronizacionUi) {
    val pendientes = when (sincronizacion.pendientes) {
        0 -> null
        1 -> "1 cambio pendiente"
        else -> "${sincronizacion.pendientes} cambios pendientes"
    }
    val texto = when {
        !sincronizacion.conectado ->
            listOfNotNull("Sin conexión: los cambios se guardan en el dispositivo", pendientes).joinToString(" · ")
        sincronizacion.sincronizando -> "Sincronizando…"
        else -> listOfNotNull(
            sincronizacion.ultimaSincronizacion?.let { "Última sincronización: $it" } ?: "Sin sincronizar todavía",
            pendientes
        ).joinToString(" · ")
    }
    val fondo = if (sincronizacion.conectado) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.errorContainer
    }

    Column {
        Surface(color = fondo, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = texto,
                style = MaterialTheme.typography.labelMedium,
                // liveRegion: TalkBack anuncia los cambios (por ejemplo, al perder la conexión)
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
        if (sincronizacion.sincronizando) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

// Un diálogo por paso del cierre de sesión. Mientras se sincroniza o se borra,
// el diálogo no se puede descartar (onDismissRequest vacío) para no cortar el proceso.
@Composable
private fun DialogosCierreSesion(
    estado: CierreSesionUi,
    onConfirmar: () -> Unit,
    onConfirmarConPendientes: () -> Unit,
    onCancelar: () -> Unit
) {
    when (estado) {
        CierreSesionUi.Inactivo -> Unit

        CierreSesionUi.Confirmando -> AlertDialog(
            onDismissRequest = onCancelar,
            title = { Text(stringResource(R.string.cerrar_sesion_confirmar_titulo)) },
            text = { Text(stringResource(R.string.cerrar_sesion_confirmar_texto)) },
            confirmButton = {
                TextButton(onClick = onConfirmar) { Text(stringResource(R.string.cerrar_sesion)) }
            },
            dismissButton = {
                TextButton(onClick = onCancelar) { Text(stringResource(R.string.cancelar)) }
            }
        )

        CierreSesionUi.Sincronizando -> DialogoProgreso(stringResource(R.string.cerrar_sesion_sincronizando))

        is CierreSesionUi.AvisoPendientes -> AlertDialog(
            onDismissRequest = onCancelar,
            icon = { Icon(Icons.Filled.Warning, contentDescription = null) },
            title = { Text(stringResource(R.string.cerrar_sesion_pendientes_titulo)) },
            text = {
                Text(
                    pluralStringResource(
                        R.plurals.cerrar_sesion_pendientes_texto,
                        estado.cantidad,
                        estado.cantidad
                    )
                )
            },
            confirmButton = {
                // Acción destructiva: en color de error
                TextButton(onClick = onConfirmarConPendientes) {
                    Text(
                        stringResource(R.string.cerrar_sesion_igual),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelar) { Text(stringResource(R.string.cancelar)) }
            }
        )

        CierreSesionUi.Cerrando -> DialogoProgreso(stringResource(R.string.cerrar_sesion_cerrando))

        CierreSesionUi.Error -> AlertDialog(
            onDismissRequest = onCancelar,
            title = { Text(stringResource(R.string.cerrar_sesion)) },
            text = { Text(stringResource(R.string.cerrar_sesion_error)) },
            confirmButton = {
                TextButton(onClick = onConfirmar) { Text(stringResource(R.string.reintentar)) }
            },
            dismissButton = {
                TextButton(onClick = onCancelar) { Text(stringResource(R.string.cancelar)) }
            }
        )
    }
}

@Composable
private fun DialogoProgreso(mensaje: String) {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Text(mensaje)
            }
        }
    )
}

// SOLO DEBUG: fuerza fallas de red para probar 4xx, 5xx, timeout y sin conexión
@Composable
private fun MenuSimuladorFallas(
    seleccionada: FallaSimulada,
    onSeleccion: (FallaSimulada) -> Unit
) {
    // Estado puramente visual (menú abierto/cerrado): puede vivir en la UI
    var expandido by remember { mutableStateOf(false) }

    IconButton(onClick = { expandido = true }) {
        Icon(Icons.Filled.Build, contentDescription = "Simular fallas de red")
    }
    DropdownMenu(expanded = expandido, onDismissRequest = { expandido = false }) {
        Text(
            "Simular falla de red",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
        FallaSimulada.entries.forEach { opcion ->
            DropdownMenuItem(
                text = { Text(opcion.etiqueta) },
                leadingIcon = { RadioButton(selected = opcion == seleccionada, onClick = null) },
                onClick = {
                    onSeleccion(opcion)
                    expandido = false
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ListadoDatosPreview() {
    RecordatoriosTheme {
        ListadoContenido(
            uiState = ListadoUiState.Datos(recordatoriosSimulados),
            sincronizacion = SincronizacionUi(pendientes = 2, ultimaSincronizacion = "30/10 21:15"),
            onCrearRecordatorio = {},
            onRecordatorioClick = {},
            onReintentar = {},
            onSincronizar = {},
            onAbrirAjustes = {},
            onCerrarSesion = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ListadoSinConexionPreview() {
    RecordatoriosTheme {
        ListadoContenido(
            uiState = ListadoUiState.Vacio,
            sincronizacion = SincronizacionUi(conectado = false, pendientes = 1),
            onCrearRecordatorio = {},
            onRecordatorioClick = {},
            onReintentar = {},
            onSincronizar = {},
            onAbrirAjustes = {},
            onCerrarSesion = {}
        )
    }
}
