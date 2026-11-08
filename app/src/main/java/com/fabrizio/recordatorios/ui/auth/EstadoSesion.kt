package com.fabrizio.recordatorios.ui.auth

// Estado de la sesión para la navegación. "Verificando" existe porque Firebase tarda
// unos milisegundos en informar si hay una sesión guardada: mientras tanto se muestra
// el splash, así no parpadea el login si el usuario ya estaba autenticado.
sealed interface EstadoSesion {
    data object Verificando : EstadoSesion
    data object SinSesion : EstadoSesion
    data class Autenticado(val uid: String) : EstadoSesion
}
