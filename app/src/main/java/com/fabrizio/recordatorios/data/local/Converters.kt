package com.fabrizio.recordatorios.data.local

import androidx.room.TypeConverter
import com.fabrizio.recordatorios.model.Categoria
import com.fabrizio.recordatorios.model.Prioridad

// Room solo guarda tipos simples. Estos converters traducen los tipos propios
// a códigos String ESTABLES (no a la etiqueta visible, que podría cambiar o traducirse).
// Ante un código desconocido se usa un valor seguro en lugar de crashear.
class Converters {

    @TypeConverter
    fun categoriaACodigo(categoria: Categoria): String = when (categoria) {
        Categoria.Estudio -> "ESTUDIO"
        Categoria.Academica -> "ACADEMICA"
        Categoria.Personal -> "PERSONAL"
        Categoria.Trabajo -> "TRABAJO"
    }

    @TypeConverter
    fun codigoACategoria(codigo: String): Categoria =
        Categoria.opciones.firstOrNull { categoriaACodigo(it) == codigo } ?: Categoria.Personal

    @TypeConverter
    fun prioridadACodigo(prioridad: Prioridad): String = when (prioridad) {
        Prioridad.Alta -> "ALTA"
        Prioridad.Media -> "MEDIA"
        Prioridad.Baja -> "BAJA"
    }

    @TypeConverter
    fun codigoAPrioridad(codigo: String): Prioridad =
        Prioridad.opciones.firstOrNull { prioridadACodigo(it) == codigo } ?: Prioridad.Media

    @TypeConverter
    fun syncStatusACodigo(status: SyncStatus): String = status.name

    // Un estado desconocido se trata como pendiente: en el peor caso se reenvía
    // y el servidor lo reconoce por clientId, en vez de perder el cambio.
    @TypeConverter
    fun codigoASyncStatus(codigo: String): SyncStatus =
        SyncStatus.entries.firstOrNull { it.name == codigo } ?: SyncStatus.PENDING_UPDATE
}
