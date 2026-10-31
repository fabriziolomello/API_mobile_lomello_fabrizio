package com.fabrizio.recordatorios.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

// Interfaz de Retrofit. Las funciones son suspend: Retrofit hace la llamada
// fuera del hilo principal. Si la respuesta no es 2xx, lanzan HttpException
// (excepto las que devuelven Response, que se revisan a mano).
interface ReminderApi {

    @GET("reminders")
    suspend fun obtenerTodos(): List<ReminderDto>

    // Búsqueda por clave de idempotencia. MockAPI responde 404 si no hay resultados.
    @GET("reminders")
    suspend fun buscarPorClientId(@Query("clientId") clientId: String): Response<List<ReminderDto>>

    @POST("reminders")
    suspend fun crear(@Body recordatorio: ReminderDto): ReminderDto

    @PUT("reminders/{id}")
    suspend fun actualizar(@Path("id") remoteId: String, @Body recordatorio: ReminderDto): ReminderDto

    @DELETE("reminders/{id}")
    suspend fun eliminar(@Path("id") remoteId: String): Response<Unit>
}
