package com.fabrizio.recordatorios.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fabrizio.recordatorios.model.FormatoFecha

// Campos "controlados": no guardan estado propio. Reciben el valor actual
// y avisan los cambios con lambdas; el ViewModel decide el nuevo estado.

@Composable
fun CampoTexto(
    valor: String,
    onValorChange: (String) -> Unit,
    etiqueta: String,
    error: String?,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    maxCaracteres: Int? = null,
    habilitado: Boolean = true
) {
    OutlinedTextField(
        value = valor,
        onValueChange = onValorChange,
        label = { Text(etiqueta) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        enabled = habilitado,
        // isError cambia el color, pero además se muestra texto, ícono y semántica
        isError = error != null,
        trailingIcon = if (error != null) {
            { Icon(Icons.Filled.Warning, contentDescription = "Error") }
        } else {
            null
        },
        supportingText = {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(text = error ?: "", modifier = Modifier.weight(1f))
                // Contador de caracteres si el campo tiene máximo
                if (maxCaracteres != null) {
                    Text(text = "${valor.length}/$maxCaracteres", textAlign = TextAlign.End)
                }
            }
        },
        modifier = modifier
            .fillMaxWidth()
            // TalkBack lee "error: <mensaje>" aunque no se vea el color
            .semantics { if (error != null) error(error) }
    )
}

// Selector de una opción entre varias (categoría, prioridad) usando chips.
// Es genérico: sirve para cualquier tipo T con una función que da su etiqueta.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> SelectorOpciones(
    titulo: String,
    opciones: List<T>,
    seleccionada: T?,
    etiquetaDe: (T) -> String,
    onSeleccion: (T) -> Unit,
    error: String?,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { if (error != null) error(error) }
    ) {
        Text(
            text = titulo,
            style = MaterialTheme.typography.titleSmall,
            color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
        // selectableGroup agrupa los chips como un grupo de opción única para accesibilidad
        FlowRow(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            opciones.forEach { opcion ->
                val estaSeleccionada = opcion == seleccionada
                FilterChip(
                    selected = estaSeleccionada,
                    onClick = { onSeleccion(opcion) },
                    enabled = habilitado,
                    label = { Text(etiquetaDe(opcion)) },
                    // El tilde marca la selección además del color
                    leadingIcon = if (estaSeleccionada) {
                        {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        }
                    } else {
                        null
                    }
                )
            }
        }
        if (error != null) {
            MensajeError(mensaje = error)
        }
    }
}

// Fecha opcional: botón que abre un DatePicker de Material 3 + botón para quitarla.
// El DatePicker trabaja en UTC (medianoche), igual que FormatoFecha.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampoFecha(
    titulo: String,
    fecha: Long?,
    onFechaSeleccionada: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true
) {
    // Diálogo abierto/cerrado: estado visual, sobrevive a la rotación
    var mostrarDialogo by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = titulo, style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { mostrarDialogo = true }, enabled = habilitado) {
                Icon(Icons.Filled.DateRange, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(fecha?.let(FormatoFecha::formatear) ?: "Elegir fecha")
            }
            if (fecha != null) {
                IconButton(onClick = { onFechaSeleccionada(null) }, enabled = habilitado) {
                    Icon(Icons.Filled.Clear, contentDescription = "Quitar fecha")
                }
            }
        }
    }

    if (mostrarDialogo) {
        val estadoPicker = rememberDatePickerState(initialSelectedDateMillis = fecha)
        DatePickerDialog(
            onDismissRequest = { mostrarDialogo = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onFechaSeleccionada(estadoPicker.selectedDateMillis)
                        mostrarDialogo = false
                    },
                    enabled = estadoPicker.selectedDateMillis != null
                ) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { mostrarDialogo = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = estadoPicker)
        }
    }
}

// Mensaje de error con ícono + texto (no depende solo del color)
@Composable
fun MensajeError(
    mensaje: String,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Filled.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = mensaje,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
