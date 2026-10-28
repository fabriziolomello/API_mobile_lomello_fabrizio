package com.fabrizio.recordatorios.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

// Escenarios que puede forzar el repositorio en memoria al cargar el listado
enum class EscenarioListado(val etiqueta: String) {
    NORMAL("Normal (datos)"),
    VACIO("Lista vacía"),
    ERROR("Error de carga"),
    CARGA_INFINITA("Carga sin fin")
}

// Configuración de la simulación (solo para pruebas y capturas, no es lógica real).
// Se cambia desde el menú "Simulación" de la pantalla de listado (builds de debug)
// o modificando los valores iniciales de abajo.
class Simulacion(
    escenarioInicial: EscenarioListado = EscenarioListado.NORMAL,
    fallarGuardadoInicial: Boolean = false,
    // Demoras que simulan una operación de red/disco
    val demoraCargaMs: Long = 1_500,
    val demoraGuardadoMs: Long = 1_200
) {
    private val _escenario = MutableStateFlow(escenarioInicial)
    val escenario: StateFlow<EscenarioListado> = _escenario

    private val _fallarGuardado = MutableStateFlow(fallarGuardadoInicial)
    val fallarGuardado: StateFlow<Boolean> = _fallarGuardado

    fun cambiarEscenario(escenario: EscenarioListado) = _escenario.update { escenario }

    fun cambiarFallarGuardado(fallar: Boolean) = _fallarGuardado.update { fallar }
}
