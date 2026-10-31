package com.fabrizio.recordatorios.data.remote

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.net.SocketTimeoutException
import java.net.UnknownHostException

// SOLO PARA PRUEBAS (builds de debug): MockAPI no permite forzar errores,
// así que este interceptor de OkHttp los fabrica para probar cada caso.
enum class FallaSimulada(val etiqueta: String) {
    NINGUNA("Sin fallas (servidor real)"),
    ERROR_400("Error 400 (solicitud inválida)"),
    ERROR_500("Error 500 (servidor)"),
    TIMEOUT("Timeout"),
    SIN_CONEXION("Sin conexión"),
    // Las escrituras (POST/PUT/DELETE) SÍ llegan al servidor, pero la app recibe un
    // timeout: sirve para demostrar que el reintento no crea un duplicado.
    TIMEOUT_TRAS_ENVIAR("Timeout después de enviar")
}

class SimuladorFallas {

    private val _falla = MutableStateFlow(FallaSimulada.NINGUNA)
    val falla: StateFlow<FallaSimulada> = _falla.asStateFlow()

    fun cambiarFalla(falla: FallaSimulada) {
        _falla.value = falla
    }

    val interceptor = Interceptor { chain ->
        val request = chain.request()
        when (_falla.value) {
            FallaSimulada.NINGUNA -> chain.proceed(request)
            FallaSimulada.ERROR_400 -> respuestaError(chain, 400, "Bad Request")
            FallaSimulada.ERROR_500 -> respuestaError(chain, 500, "Internal Server Error")
            FallaSimulada.TIMEOUT -> {
                Thread.sleep(DEMORA_MS) // corre en un hilo de OkHttp, no en el principal
                throw SocketTimeoutException("Timeout simulado")
            }
            FallaSimulada.SIN_CONEXION -> throw UnknownHostException("Sin conexión simulada")
            FallaSimulada.TIMEOUT_TRAS_ENVIAR ->
                if (request.method == "GET") {
                    chain.proceed(request) // las consultas funcionan normal
                } else {
                    chain.proceed(request).close() // el servidor procesa la escritura...
                    throw SocketTimeoutException("Timeout simulado después de enviar") // ...pero la respuesta "se pierde"
                }
        }
    }

    private fun respuestaError(chain: Interceptor.Chain, codigo: Int, mensaje: String): Response =
        Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(codigo)
            .message(mensaje)
            .body("\"$mensaje (simulado)\"".toResponseBody("application/json".toMediaType()))
            .build()

    private companion object {
        const val DEMORA_MS = 1_500L
    }
}
