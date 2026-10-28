package com.fabrizio.recordatorios

import android.app.Application
import com.fabrizio.recordatorios.data.RecordatorioRepository
import com.fabrizio.recordatorios.data.RecordatorioRepositoryEnMemoria
import com.fabrizio.recordatorios.data.Simulacion

// Inyección de dependencias "manual": la Application crea una sola instancia
// del repositorio y de la simulación, y las factories de los ViewModel las toman de acá.
// Vive mientras viva el proceso, así los datos en memoria sobreviven a rotaciones.
class RecordatoriosApplication : Application() {

    // Para forzar un estado desde el arranque, cambiar los valores iniciales, por ejemplo:
    // Simulacion(escenarioInicial = EscenarioListado.ERROR, fallarGuardadoInicial = true)
    val simulacion: Simulacion by lazy { Simulacion() }

    // Se expone como la interfaz: el resto de la app no conoce la implementación
    val repositorio: RecordatorioRepository by lazy { RecordatorioRepositoryEnMemoria(simulacion) }
}
