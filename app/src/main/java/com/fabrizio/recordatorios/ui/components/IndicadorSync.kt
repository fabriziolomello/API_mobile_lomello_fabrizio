package com.fabrizio.recordatorios.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fabrizio.recordatorios.model.EstadoSync

// Estado de sincronización de un recordatorio: ícono + texto (no depende solo del color)
@Composable
fun IndicadorSync(
    estado: EstadoSync,
    modifier: Modifier = Modifier
) {
    val (icono, color) = when (estado) {
        EstadoSync.PENDIENTE -> Icons.Filled.Refresh to MaterialTheme.colorScheme.tertiary
        EstadoSync.SINCRONIZADO -> Icons.Filled.CheckCircle to MaterialTheme.colorScheme.primary
        EstadoSync.ERROR -> Icons.Filled.Warning to MaterialTheme.colorScheme.error
    }
    Row(
        modifier = modifier.semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icono, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = estado.etiqueta, color = color, style = MaterialTheme.typography.labelMedium)
    }
}
