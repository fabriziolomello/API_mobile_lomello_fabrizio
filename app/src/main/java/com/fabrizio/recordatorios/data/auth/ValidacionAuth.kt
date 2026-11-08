package com.fabrizio.recordatorios.data.auth

// Validación local antes de llamar a Firebase (evita viajes a la red con datos inválidos).
// Funciones puras, sin Android (android.util.Patterns no existe en los tests JVM).
object ValidacionAuth {

    // Mismo mínimo que exige Firebase Auth por defecto
    const val CONTRASENA_MIN_CARACTERES = 6

    // Formato básico: algo@algo.algo, sin espacios
    private val FORMATO_CORREO = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    fun correoValido(correo: String): Boolean = FORMATO_CORREO.matches(correo.trim())

    fun contrasenaValida(contrasena: String): Boolean = contrasena.length >= CONTRASENA_MIN_CARACTERES

    // null si los datos son válidos; si no, el primer error a mostrar
    fun validar(correo: String, contrasena: String): AuthError? = when {
        !correoValido(correo) -> AuthError.CorreoInvalido
        !contrasenaValida(contrasena) -> AuthError.ContrasenaDebil
        else -> null
    }
}
