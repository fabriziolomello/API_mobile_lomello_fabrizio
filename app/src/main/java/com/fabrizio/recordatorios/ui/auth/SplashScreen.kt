package com.fabrizio.recordatorios.ui.auth

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.fabrizio.recordatorios.R
import com.fabrizio.recordatorios.ui.components.IndicadorCarga

// Se ve solo mientras Firebase informa si hay una sesión guardada (normalmente unos
// milisegundos). Evita que parpadee el login cuando el usuario ya estaba autenticado.
@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize()) {
        IndicadorCarga(mensaje = stringResource(R.string.inicio_verificando_sesion))
    }
}
