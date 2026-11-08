package com.fabrizio.recordatorios

import android.app.Application

// La Application crea un solo AppContainer, que vive mientras viva el proceso.
// Así el repositorio y DataStore son únicos y sobreviven a rotaciones.
class RecordatoriosApplication : Application() {

    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // Con sesión iniciada: sincroniza al abrir la app y cada vez que vuelve la conexión.
        // Sin sesión no sincroniza (ver AppContainer.vincularSincronizacionASesion).
        container.vincularSincronizacionASesion()
    }
}
