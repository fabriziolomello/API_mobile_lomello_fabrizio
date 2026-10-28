package com.fabrizio.recordatorios.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fabrizio.recordatorios.data.colorPorCategoria
import com.fabrizio.recordatorios.data.recordatoriosSimulados
import com.fabrizio.recordatorios.model.Estado
import com.fabrizio.recordatorios.model.Recordatorio
import com.fabrizio.recordatorios.ui.theme.RecordatoriosTheme

// Tarjeta de un recordatorio (viene de la Actividad 1, ahora clickeable).
// No sabe nada de navegación: solo avisa el click con una lambda.
@Composable
fun TarjetaRecordatorio(
    recordatorio: Recordatorio,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Color de la categoría tomado del Map; si no está, se usa gris
    val colorCategoria = colorPorCategoria[recordatorio.categoria] ?: Color.Gray
    val estaCompletado = recordatorio.estado == Estado.Completado

    Card(
        onClick = onClick,
        // Etiqueta para lectores de pantalla: "doble toque para ver detalle"
        modifier = modifier
            .fillMaxWidth()
            .semantics { onClick(label = "Ver detalle", action = null) }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Título: tachado si el recordatorio está completado
            Text(
                text = recordatorio.titulo,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textDecoration = if (estaCompletado) TextDecoration.LineThrough else null
            )

            Spacer(modifier = Modifier.padding(top = 4.dp))

            // Categoría con un círculo del color correspondiente (el texto acompaña al color)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(colorCategoria, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = recordatorio.categoria.etiqueta,
                    color = colorCategoria,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = "Prioridad: ${recordatorio.prioridad.etiqueta}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Estado: ${recordatorio.estado.etiqueta}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TarjetaRecordatorioPreview() {
    RecordatoriosTheme {
        TarjetaRecordatorio(recordatorio = recordatoriosSimulados.first(), onClick = {})
    }
}
