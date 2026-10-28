package com.fabrizio.recordatorios

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fabrizio.recordatorios.navigation.RecordatoriosApp
import com.fabrizio.recordatorios.ui.theme.RecordatoriosTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RecordatoriosTheme {
                // Cada pantalla tiene su propio Scaffold y aplica su innerPadding
                RecordatoriosApp()
            }
        }
    }
}
