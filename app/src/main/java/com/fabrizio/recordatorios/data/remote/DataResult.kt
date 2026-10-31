package com.fabrizio.recordatorios.data.remote

import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

// Resultado de una llamada al servidor. Las excepciones de red se traducen ACÁ,
// en la capa de datos: ni los ViewModel ni las pantallas ven HttpException o IOException.
sealed interface DataResult<out T> {

    data class Exito<T>(val datos: T) : DataResult<T>

    sealed interface Error : DataResult<Nothing> {
        // true: vale la pena reintentar más tarde (problema temporal de red o servidor)
        val recuperable: Boolean
        val mensajeUsuario: String
    }

    // 4xx: la solicitud es inválida; reenviarla igual no sirve
    data class ErrorCliente(val codigo: Int) : Error {
        override val recuperable = false
        override val mensajeUsuario =
            "El servidor rechazó la solicitud (error $codigo). Revisá el recordatorio y reintentá."
    }

    // 5xx: falla del servidor; los datos quedan guardados y se reintenta después
    data class ErrorServidor(val codigo: Int) : Error {
        override val recuperable = true
        override val mensajeUsuario =
            "El servidor tuvo un problema (error $codigo). Tus cambios quedan guardados en el dispositivo."
    }

    data object Timeout : Error {
        override val recuperable = true
        override val mensajeUsuario =
            "El servidor tardó demasiado en responder. Tus cambios quedan guardados en el dispositivo."
    }

    data object SinConexion : Error {
        override val recuperable = true
        override val mensajeUsuario =
            "Sin conexión. Tus cambios quedan guardados y se enviarán al reconectar."
    }

    data class Desconocido(val causa: Throwable) : Error {
        override val recuperable = true
        override val mensajeUsuario = "Ocurrió un error inesperado al sincronizar. Intentá de nuevo."
    }
}

// Ejecuta una llamada y clasifica cualquier falla en un DataResult.Error.
// El orden importa: SocketTimeoutException es una IOException, por eso va antes.
suspend fun <T> llamarApi(llamada: suspend () -> T): DataResult<T> =
    try {
        DataResult.Exito(llamada())
    } catch (e: CancellationException) {
        throw e // cancelar una corrutina no es un error de red
    } catch (e: HttpException) {
        errorHttp(e.code())
    } catch (e: SocketTimeoutException) {
        DataResult.Timeout
    } catch (e: IOException) {
        // UnknownHostException, ConnectException, etc.: no hay red o no llega al servidor
        DataResult.SinConexion
    } catch (e: Exception) {
        // Por ejemplo, un JSON que no respeta el contrato (SerializationException)
        DataResult.Desconocido(e)
    }

fun errorHttp(codigo: Int): DataResult.Error =
    if (codigo in 400..499) DataResult.ErrorCliente(codigo) else DataResult.ErrorServidor(codigo)
