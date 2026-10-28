package com.fabrizio.recordatorios.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fabrizio.recordatorios.ui.theme.RecordatoriosTheme

// Componentes para los estados de "pantalla completa": carga, vacío y error.
// Se reutilizan en el listado y en el detalle.

@Composable
fun IndicadorCarga(
    mensaje: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            // El lector de pantalla anuncia el mensaje cuando aparece
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Text(
            text = mensaje,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}

// Estado vacío: mensaje + botón de acción (por ejemplo, "Crear recordatorio")
@Composable
fun EstadoVacio(
    mensaje: String,
    textoAccion: String,
    onAccion: () -> Unit,
    modifier: Modifier = Modifier
) {
    MensajeConAccion(
        icono = Icons.Filled.Info,
        colorIcono = MaterialTheme.colorScheme.primary,
        mensaje = mensaje,
        textoAccion = textoAccion,
        onAccion = onAccion,
        modifier = modifier
    )
}

// Estado de error: mensaje comprensible + botón de reintento
@Composable
fun EstadoError(
    mensaje: String,
    onReintentar: () -> Unit,
    modifier: Modifier = Modifier,
    textoAccion: String = "Reintentar"
) {
    MensajeConAccion(
        icono = Icons.Filled.Warning,
        colorIcono = MaterialTheme.colorScheme.error,
        mensaje = mensaje,
        textoAccion = textoAccion,
        onAccion = onReintentar,
        modifier = modifier
    )
}

// Base común de los dos anteriores: ícono + texto + botón, centrados
@Composable
private fun MensajeConAccion(
    icono: ImageVector,
    colorIcono: Color,
    mensaje: String,
    textoAccion: String,
    onAccion: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Ícono decorativo: el significado lo da el texto (contentDescription = null)
        Icon(
            imageVector = icono,
            contentDescription = null,
            tint = colorIcono,
            modifier = Modifier.size(48.dp)
        )
        Text(
            text = mensaje,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
        Button(onClick = onAccion) {
            Text(textoAccion)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EstadoErrorPreview() {
    RecordatoriosTheme {
        EstadoError(mensaje = "No pudimos cargar los recordatorios.", onReintentar = {})
    }
}
