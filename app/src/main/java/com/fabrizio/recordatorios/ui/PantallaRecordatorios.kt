package com.fabrizio.recordatorios.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fabrizio.recordatorios.data.colorPorCategoria
import com.fabrizio.recordatorios.data.filtrarPorEstado
import com.fabrizio.recordatorios.data.filtrarPorPrioridad
import com.fabrizio.recordatorios.data.recordatoriosSimulados
import com.fabrizio.recordatorios.model.Estado
import com.fabrizio.recordatorios.model.Prioridad
import com.fabrizio.recordatorios.model.Recordatorio
import com.fabrizio.recordatorios.ui.theme.RecordatoriosTheme

// Opciones de filtro. Se usa un enum porque rememberSaveable
// puede guardarlo automáticamente (por ejemplo, al rotar la pantalla).
enum class Filtro(val etiqueta: String) {
    TODOS("Todos"),
    PENDIENTES("Pendientes"),
    COMPLETADOS("Completados"),
    PRIORIDAD_ALTA("Prioridad alta")
}

// Pantalla principal: chips de filtro arriba y la lista de recordatorios abajo
@Composable
fun PantallaRecordatorios(modifier: Modifier = Modifier) {
    // Filtro seleccionado; rememberSaveable lo conserva ante cambios de configuración
    var filtroSeleccionado by rememberSaveable { mutableStateOf(Filtro.TODOS) }

    // Lista que se muestra según el filtro elegido (usa las funciones de extensión)
    val recordatoriosFiltrados = when (filtroSeleccionado) {
        Filtro.TODOS -> recordatoriosSimulados
        Filtro.PENDIENTES -> recordatoriosSimulados.filtrarPorEstado(Estado.Pendiente)
        Filtro.COMPLETADOS -> recordatoriosSimulados.filtrarPorEstado(Estado.Completado)
        Filtro.PRIORIDAD_ALTA -> recordatoriosSimulados.filtrarPorPrioridad(Prioridad.Alta)
    }

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = "Mis recordatorios",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp)
        )

        // Fila de chips con scroll horizontal por si no entran en pantallas chicas
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Se crea un FilterChip por cada opción del enum
            Filtro.entries.forEach { filtro ->
                FilterChip(
                    selected = filtroSeleccionado == filtro,
                    onClick = { filtroSeleccionado = filtro },
                    label = { Text(filtro.etiqueta) }
                )
            }
        }

        // LazyColumn solo dibuja los elementos visibles en pantalla
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // key = id ayuda a Compose a identificar cada elemento
            items(recordatoriosFiltrados, key = { it.id }) { recordatorio ->
                TarjetaRecordatorio(recordatorio = recordatorio)
            }
        }
    }
}

// Tarjeta que muestra los datos de un recordatorio
@Composable
fun TarjetaRecordatorio(recordatorio: Recordatorio, modifier: Modifier = Modifier) {
    // Color de la categoría tomado del Map; si no está, se usa gris
    val colorCategoria = colorPorCategoria[recordatorio.categoria] ?: Color.Gray
    val estaCompletado = recordatorio.estado == Estado.Completado

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Título: tachado si el recordatorio está completado
            Text(
                text = recordatorio.titulo,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textDecoration = if (estaCompletado) TextDecoration.LineThrough else null
            )

            Spacer(modifier = Modifier.padding(top = 4.dp))

            // Categoría con un círculo del color correspondiente
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

            // Prioridad y estado
            Text(
                text = "Prioridad: ${recordatorio.prioridad.etiqueta}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Estado: ${recordatorio.estado.etiqueta}",
                style = MaterialTheme.typography.bodyMedium
            )

            // Campos opcionales: ?.let solo dibuja el Text si el valor no es null
            recordatorio.descripcion?.let { descripcion ->
                Text(
                    text = descripcion,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            recordatorio.fechaLimite?.let { fecha ->
                Text(
                    text = "Fecha límite: $fecha",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

// Vista previa para ver la pantalla en Android Studio sin ejecutar la app
@Preview(showBackground = true)
@Composable
fun PantallaRecordatoriosPreview() {
    RecordatoriosTheme {
        PantallaRecordatorios()
    }
}
