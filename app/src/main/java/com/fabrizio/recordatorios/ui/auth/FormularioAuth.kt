package com.fabrizio.recordatorios.ui.auth

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fabrizio.recordatorios.R
import com.fabrizio.recordatorios.data.auth.AuthError
import com.fabrizio.recordatorios.data.auth.ValidacionAuth
import com.fabrizio.recordatorios.ui.components.MensajeError
import com.fabrizio.recordatorios.ui.theme.RecordatoriosTheme

// Formulario "sin estado" compartido por login y registro: solo dibuja lo que recibe
// y avisa los eventos con lambdas. Los textos que cambian llegan como recursos.
@Composable
fun FormularioAuth(
    @StringRes titulo: Int,
    @StringRes textoBoton: Int,
    @StringRes textoEnlace: Int,
    correo: String,
    onCorreoChange: (String) -> Unit,
    contrasena: String,
    onContrasenaChange: (String) -> Unit,
    mostrarContrasena: Boolean,
    onAlternarMostrarContrasena: () -> Unit,
    uiState: AuthUiState,
    onEnviar: () -> Unit,
    onEnlace: () -> Unit,
    modifier: Modifier = Modifier,
    mostrarAyudaContrasena: Boolean = false
) {
    val focusManager = LocalFocusManager.current
    val teclado = LocalSoftwareKeyboardController.current
    // Cierra el teclado: lo oculta explícitamente y quita el foco del campo
    val cerrarTeclado = {
        teclado?.hide()
        focusManager.clearFocus()
    }
    val habilitado = !uiState.cargando
    val error = uiState.error

    // Los errores de un campo se muestran debajo de ese campo; el resto, arriba del botón
    val errorCorreo = (error as? AuthError.CorreoInvalido)?.let { it.mensaje() }
    val errorContrasena = (error as? AuthError.ContrasenaDebil)?.let { it.mensaje() }
    val errorGeneral = error?.takeIf { errorCorreo == null && errorContrasena == null }?.mensaje()

    Scaffold(modifier = modifier) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                // El contenido sube con el teclado (la Activity usa adjustResize)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(titulo),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() }
            )

            OutlinedTextField(
                value = correo,
                onValueChange = onCorreoChange,
                label = { Text(stringResource(R.string.auth_correo)) },
                singleLine = true,
                enabled = habilitado,
                isError = errorCorreo != null,
                supportingText = errorCorreo?.let { { Text(it) } },
                // Teclado de correo; "Siguiente" pasa a la contraseña
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { if (errorCorreo != null) error(errorCorreo) }
            )

            val ayuda = if (mostrarAyudaContrasena) {
                stringResource(R.string.auth_ayuda_contrasena, ValidacionAuth.CONTRASENA_MIN_CARACTERES)
            } else {
                null
            }
            OutlinedTextField(
                value = contrasena,
                onValueChange = onContrasenaChange,
                label = { Text(stringResource(R.string.auth_contrasena)) },
                singleLine = true,
                enabled = habilitado,
                isError = errorContrasena != null,
                supportingText = (errorContrasena ?: ayuda)?.let { { Text(it) } },
                visualTransformation = if (mostrarContrasena) VisualTransformation.None else PasswordVisualTransformation(),
                // "Listo" cierra el teclado (no envía: el envío es solo con el botón)
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { cerrarTeclado() }),
                trailingIcon = {
                    // Botón de texto (los íconos de "ojo" no están en material-icons-core).
                    // TalkBack lee la descripción completa en lugar de "Mostrar".
                    val descripcion = stringResource(
                        if (mostrarContrasena) R.string.auth_ocultar_contrasena else R.string.auth_mostrar_contrasena
                    )
                    TextButton(
                        onClick = onAlternarMostrarContrasena,
                        enabled = habilitado,
                        modifier = Modifier.semantics { contentDescription = descripcion }
                    ) {
                        Text(stringResource(if (mostrarContrasena) R.string.auth_ocultar else R.string.auth_mostrar))
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { if (errorContrasena != null) error(errorContrasena) }
            )

            if (errorGeneral != null) {
                // liveRegion: TalkBack anuncia el error apenas aparece
                MensajeError(
                    mensaje = errorGeneral,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
                )
            }

            // Mientras carga: botón deshabilitado + indicador (evita dobles envíos)
            Button(
                onClick = {
                    cerrarTeclado()
                    onEnviar()
                },
                enabled = habilitado,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.cargando) {
                    val procesando = stringResource(R.string.auth_procesando)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier
                                .size(18.dp)
                                .semantics { contentDescription = procesando }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(textoBoton))
                    }
                } else {
                    Text(stringResource(textoBoton))
                }
            }

            TextButton(onClick = onEnlace, enabled = habilitado) {
                Text(stringResource(textoEnlace))
            }
        }
    }
}

// Mensaje en español para cada AuthError (los textos viven en strings.xml)
@Composable
fun AuthError.mensaje(): String = when (this) {
    AuthError.CorreoInvalido -> stringResource(R.string.error_auth_correo_invalido)
    AuthError.ContrasenaDebil ->
        stringResource(R.string.error_auth_contrasena_debil, ValidacionAuth.CONTRASENA_MIN_CARACTERES)
    AuthError.CorreoYaRegistrado -> stringResource(R.string.error_auth_correo_registrado)
    AuthError.CredencialesIncorrectas -> stringResource(R.string.error_auth_credenciales)
    AuthError.SinRed -> stringResource(R.string.error_auth_sin_red)
    is AuthError.Desconocido -> stringResource(R.string.error_auth_desconocido)
}

@Preview(showBackground = true)
@Composable
private fun FormularioAuthErrorPreview() {
    RecordatoriosTheme {
        FormularioAuth(
            titulo = R.string.login_titulo,
            textoBoton = R.string.login_boton,
            textoEnlace = R.string.login_ir_a_registro,
            correo = "ana@mail.com",
            onCorreoChange = {},
            contrasena = "secreta",
            onContrasenaChange = {},
            mostrarContrasena = false,
            onAlternarMostrarContrasena = {},
            uiState = AuthUiState(sesionVerificada = true, error = AuthError.CredencialesIncorrectas),
            onEnviar = {},
            onEnlace = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FormularioAuthCargandoPreview() {
    RecordatoriosTheme {
        FormularioAuth(
            titulo = R.string.registro_titulo,
            textoBoton = R.string.registro_boton,
            textoEnlace = R.string.registro_ir_a_login,
            correo = "ana@mail.com",
            onCorreoChange = {},
            contrasena = "secreta",
            onContrasenaChange = {},
            mostrarContrasena = true,
            onAlternarMostrarContrasena = {},
            uiState = AuthUiState(sesionVerificada = true, cargando = true),
            onEnviar = {},
            onEnlace = {},
            mostrarAyudaContrasena = true
        )
    }
}
