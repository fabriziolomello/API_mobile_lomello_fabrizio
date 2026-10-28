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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

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
