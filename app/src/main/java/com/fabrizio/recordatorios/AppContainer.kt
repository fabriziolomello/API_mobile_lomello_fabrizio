package com.fabrizio.recordatorios

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.fabrizio.recordatorios.data.RecordatorioRepository
import com.fabrizio.recordatorios.data.RecordatorioRepositoryEnMemoria
import com.fabrizio.recordatorios.data.Simulacion
import com.fabrizio.recordatorios.data.preferencias.PreferenciasRepository
import com.fabrizio.recordatorios.data.preferencias.PreferenciasRepositoryDataStore

// Delegate a nivel de archivo: garantiza UNA sola instancia de DataStore por proceso.
// Tener dos instancias sobre el mismo archivo hace que DataStore lance una excepción.
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "preferencias_usuario")

// Inyección de dependencias manual: un único lugar donde se crean las dependencias
// de la app. Las factories de los ViewModel las toman de acá.
class AppContainer(context: Context) {

    // Se guarda el applicationContext para no retener una Activity
    private val appContext = context.applicationContext

    // Para forzar un estado desde el arranque, cambiar los valores iniciales, por ejemplo:
    // Simulacion(escenarioInicial = EscenarioListado.ERROR, fallarGuardadoInicial = true)
    val simulacion: Simulacion by lazy { Simulacion() }

    // Se expone como la interfaz: el resto de la app no conoce la implementación
    val repositorio: RecordatorioRepository by lazy { RecordatorioRepositoryEnMemoria(simulacion) }

    val preferencias: PreferenciasRepository by lazy {
        PreferenciasRepositoryDataStore(appContext.dataStore)
    }
}
