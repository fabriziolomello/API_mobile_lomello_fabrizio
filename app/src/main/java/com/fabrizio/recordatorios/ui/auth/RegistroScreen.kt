package com.fabrizio.recordatorios.ui.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fabrizio.recordatorios.R

// Versión "con estado" del registro. Firebase inicia sesión al crear la cuenta,
// así que la navegación al listado la hace el Flow de sesión, igual que en el login.
@Composable
fun RegistroScreen(
    onIrALogin: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var correo by rememberSaveable { mutableStateOf("") }
    var contrasena by remember { mutableStateOf("") } // no se guarda en el Bundle
    var mostrarContrasena by rememberSaveable { mutableStateOf(false) }

    FormularioAuth(
        titulo = R.string.registro_titulo,
        textoBoton = R.string.registro_boton,
        textoEnlace = R.string.registro_ir_a_login,
        correo = correo,
        onCorreoChange = {
            correo = it
            viewModel.limpiarError()
        },
        contrasena = contrasena,
        onContrasenaChange = {
            contrasena = it
            viewModel.limpiarError()
        },
        mostrarContrasena = mostrarContrasena,
        onAlternarMostrarContrasena = { mostrarContrasena = !mostrarContrasena },
        uiState = uiState,
        onEnviar = { viewModel.register(correo, contrasena) },
        onEnlace = onIrALogin,
        modifier = modifier,
        // En el registro se muestra la regla de la contraseña antes de que falle
        mostrarAyudaContrasena = true
    )
}
