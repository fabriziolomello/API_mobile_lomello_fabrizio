package com.fabrizio.recordatorios

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fabrizio.recordatorios.data.preferencias.TemaApp
import com.fabrizio.recordatorios.navigation.RecordatoriosApp
import com.fabrizio.recordatorios.ui.theme.RecordatoriosTheme

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels { MainViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val tema by mainViewModel.tema.collectAsStateWithLifecycle()
            val sesion by mainViewModel.sesion.collectAsStateWithLifecycle()

            // Mientras se lee DataStore (milisegundos) no se dibuja nada,
            // así no aparece un tema y enseguida el otro.
            tema?.let { temaElegido ->
                val oscuro = when (temaElegido) {
                    TemaApp.CLARO -> false
                    TemaApp.OSCURO -> true
                    TemaApp.SISTEMA -> isSystemInDarkTheme()
                }

                // Los íconos de la barra de estado siguen al tema de la app, no al del sistema
                DisposableEffect(oscuro) {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { oscuro },
                        navigationBarStyle = SystemBarStyle.auto(SCRIM_CLARO, SCRIM_OSCURO) { oscuro }
                    )
                    onDispose {}
                }

                RecordatoriosTheme(darkTheme = oscuro) {
                    // Cada pantalla tiene su propio Scaffold y aplica su innerPadding
                    RecordatoriosApp(sesion = sesion)
                }
            }
        }
    }

    private companion object {
        // Mismos valores que usa enableEdgeToEdge por defecto para la barra de navegación
        val SCRIM_CLARO = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val SCRIM_OSCURO = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
