package com.fabrizio.recordatorios.data.remote

import com.fabrizio.recordatorios.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

// Construcción de OkHttp + Retrofit (la usa el AppContainer)
object NetworkModule {

    // ignoreUnknownKeys: si el servidor agrega campos, la app no se rompe.
    // encodeDefaults: se envían TODOS los campos, aunque valgan lo mismo que el default
    // del DTO (completed = false, description = null). Sin esto se omiten, y MockAPI
    // conserva el valor viejo en un PUT o rellena el campo con datos aleatorios.
    internal val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun crearApi(baseUrl: String, simuladorFallas: SimuladorFallas): ReminderApi {
        val cliente = OkHttpClient.Builder()
            // Timeouts explícitos: sin ellos una red lenta podría colgar el sync mucho tiempo
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .apply {
                // Solo en debug: log de peticiones/respuestas en Logcat (tag okhttp.OkHttpClient)
                // y simulador de fallas. El log va primero para registrar también las fallas simuladas.
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
                    addInterceptor(simuladorFallas.interceptor)
                }
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(cliente)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ReminderApi::class.java)
    }
}
