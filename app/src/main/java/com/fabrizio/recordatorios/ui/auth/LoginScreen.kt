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

// Versión "con estado" del login. Al autenticarse NO navega: la navegación reacciona
// al Flow de sesión (ver RecordatoriosApp).
@Composable
fun LoginScreen(
    onIrARegistro: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Texto de los campos: estado de UI. El correo sobrevive a la rotación;
    // la contraseña usa remember (no rememberSaveable) para no guardarla en el Bundle.
    var correo by rememberSaveable { mutableStateOf("") }
    var contrasena by remember { mutableStateOf("") }
    var mostrarContrasena by rememberSaveable { mutableStateOf(false) }

    FormularioAuth(
        titulo = R.string.login_titulo,
        textoBoton = R.string.login_boton,
        textoEnlace = R.string.login_ir_a_registro,
        correo = correo,
        onCorreoChange = {
            correo = it
            viewModel.limpiarError() // el error se va apenas el usuario corrige
        },
        contrasena = contrasena,
        onContrasenaChange = {
            contrasena = it
            viewModel.limpiarError()
        },
        mostrarContrasena = mostrarContrasena,
        onAlternarMostrarContrasena = { mostrarContrasena = !mostrarContrasena },
        uiState = uiState,
        onEnviar = { viewModel.login(correo, contrasena) },
        onEnlace = onIrARegistro,
        modifier = modifier
    )
}
