package com.fabrizio.recordatorios.data.remote

import retrofit2.HttpException

// Acceso al servidor ya "traducido" a DataResult. Interfaz para poder probar
// la sincronización con un servidor falso.
interface ReminderRemoteDataSource {
    suspend fun obtenerTodos(): DataResult<List<ReminderDto>>

    // Exito(null) si el servidor no tiene ningún registro con ese clientId
    suspend fun buscarPorClientId(clientId: String): DataResult<ReminderDto?>

    suspend fun crear(dto: ReminderDto): DataResult<ReminderDto>
    suspend fun actualizar(remoteId: String, dto: ReminderDto): DataResult<ReminderDto>
    suspend fun eliminar(remoteId: String): DataResult<Unit>
}

class RetrofitReminderRemoteDataSource(
    private val api: ReminderApi
) : ReminderRemoteDataSource {

    override suspend fun obtenerTodos() = llamarApi { api.obtenerTodos() }

    override suspend fun buscarPorClientId(clientId: String): DataResult<ReminderDto?> = llamarApi {
        val respuesta = api.buscarPorClientId(clientId)
        when {
            // MockAPI responde 404 cuando el filtro no encuentra nada: no es un error
            respuesta.code() == 404 -> null
            !respuesta.isSuccessful -> throw HttpException(respuesta)
            // El filtro de MockAPI es por coincidencia parcial: se exige igualdad exacta
            else -> respuesta.body().orEmpty().firstOrNull { it.clientId == clientId }
        }
    }

    override suspend fun crear(dto: ReminderDto) = llamarApi { api.crear(dto) }

    override suspend fun actualizar(remoteId: String, dto: ReminderDto) =
        llamarApi { api.actualizar(remoteId, dto) }

    override suspend fun eliminar(remoteId: String): DataResult<Unit> = llamarApi {
        val respuesta = api.eliminar(remoteId)
        // 404 = ya no existe: el objetivo del DELETE está cumplido (reintento idempotente)
        if (!respuesta.isSuccessful && respuesta.code() != 404) throw HttpException(respuesta)
    }
}
