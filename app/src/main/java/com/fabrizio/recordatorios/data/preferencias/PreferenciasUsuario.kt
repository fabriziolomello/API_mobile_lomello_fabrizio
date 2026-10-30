package com.fabrizio.recordatorios.data.preferencias

import com.fabrizio.recordatorios.model.Prioridad

// Opciones de cada preferencia. En DataStore se guarda el `name` del enum como String;
// al leer, un valor desconocido vuelve al valor por defecto (ver PreferenciasRepository).

enum class TemaApp(val etiqueta: String) {
    SISTEMA("Sistema"),
    CLARO("Claro"),
    OSCURO("Oscuro")
}

enum class CriterioOrden(val etiqueta: String) {
    CREACION("Más recientes"),
    PRIORIDAD("Prioridad"),
    TITULO("Título (A-Z)")
}

enum class FiltroPrioridad(val etiqueta: String, val prioridad: Prioridad?) {
    TODAS("Todas", null),
    ALTA("Alta", Prioridad.Alta),
    MEDIA("Media", Prioridad.Media),
    BAJA("Baja", Prioridad.Baja)
}

// Modelo propio de preferencias: el resto de la app usa esto y nunca ve `Preferences`.
// Solo configuración liviana de la UI; los recordatorios NO se guardan acá (van en Room).
data class PreferenciasUsuario(
    val tema: TemaApp = TemaApp.SISTEMA,
    val mostrarCompletados: Boolean = true,
    val orden: CriterioOrden = CriterioOrden.CREACION,
    val filtroPrioridad: FiltroPrioridad = FiltroPrioridad.TODAS
)
