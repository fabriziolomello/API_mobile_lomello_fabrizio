package com.fabrizio.recordatorios.data.preferencias

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import kotlin.enums.enumEntries

// Contrato de preferencias: los ViewModel dependen de esta interfaz, no de DataStore.
interface PreferenciasRepository {

    // Siempre emite un valor válido (con valores por defecto si no hay datos o hubo error)
    val preferencias: Flow<PreferenciasUsuario>

    // Escrituras suspend: DataStore hace el trabajo de disco fuera del hilo principal
    suspend fun guardarTema(tema: TemaApp)
    suspend fun guardarMostrarCompletados(mostrar: Boolean)
    suspend fun guardarOrden(orden: CriterioOrden)
    suspend fun guardarFiltroPrioridad(filtro: FiltroPrioridad)

    // Hora (epoch millis) del último sync exitoso; null si nunca se sincronizó.
    // Es un dato chico de estado, no una colección: por eso puede ir en DataStore.
    val ultimaSincronizacion: Flow<Long?>
    suspend fun guardarUltimaSincronizacion(momento: Long)

    // Al cerrar sesión: borra lo ligado al usuario (último sync y filtros del listado).
    // El tema se conserva: es una preferencia del dispositivo.
    suspend fun borrarDatosDeSesion()

    // Solo para pruebas (builds de debug): guarda un tema inexistente para
    // demostrar que la lectura vuelve al valor por defecto sin romper la app.
    suspend fun guardarTemaInvalidoParaPrueba()
}

class PreferenciasRepositoryDataStore(
    // Se recibe la ÚNICA instancia de DataStore (creada en AppContainer)
    private val dataStore: DataStore<Preferences>
) : PreferenciasRepository {

    // Claves tipadas: el tipo de cada valor queda fijado en la clave
    private object Claves {
        val TEMA = stringPreferencesKey("tema")
        val MOSTRAR_COMPLETADOS = booleanPreferencesKey("mostrar_completados")
        val ORDEN = stringPreferencesKey("orden")
        val FILTRO_PRIORIDAD = stringPreferencesKey("filtro_prioridad")
        val ULTIMA_SINCRONIZACION = longPreferencesKey("ultima_sincronizacion")
    }

    // Lectura segura compartida: IOException -> preferencias vacías (valores por defecto)
    private val datosSeguros: Flow<Preferences> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }

    // Error de lectura recuperable (archivo dañado o inaccesible): se usan los defaults.
    // Cualquier otra excepción es un bug y se deja propagar (ver datosSeguros).
    override val preferencias: Flow<PreferenciasUsuario> = datosSeguros
        .map { it.toPreferenciasUsuario() }
        .distinctUntilChanged()

    override val ultimaSincronizacion: Flow<Long?> = datosSeguros
        .map { it[Claves.ULTIMA_SINCRONIZACION] }
        .distinctUntilChanged()

    override suspend fun guardarUltimaSincronizacion(momento: Long) {
        dataStore.edit { it[Claves.ULTIMA_SINCRONIZACION] = momento }
    }

    override suspend fun borrarDatosDeSesion() {
        dataStore.edit {
            it.remove(Claves.ULTIMA_SINCRONIZACION)
            it.remove(Claves.MOSTRAR_COMPLETADOS)
            it.remove(Claves.ORDEN)
            it.remove(Claves.FILTRO_PRIORIDAD)
        }
    }

    override suspend fun guardarTema(tema: TemaApp) {
        dataStore.edit { it[Claves.TEMA] = tema.name }
    }

    override suspend fun guardarMostrarCompletados(mostrar: Boolean) {
        dataStore.edit { it[Claves.MOSTRAR_COMPLETADOS] = mostrar }
    }

    override suspend fun guardarOrden(orden: CriterioOrden) {
        dataStore.edit { it[Claves.ORDEN] = orden.name }
    }

    override suspend fun guardarFiltroPrioridad(filtro: FiltroPrioridad) {
        dataStore.edit { it[Claves.FILTRO_PRIORIDAD] = filtro.name }
    }

    override suspend fun guardarTemaInvalidoParaPrueba() {
        dataStore.edit { it[Claves.TEMA] = "VIOLETA" }
    }

    // --- Mapeo Preferences -> modelo propio ---
    // Si falta una clave o el valor guardado no corresponde a ninguna opción,
    // se usa el valor por defecto del data class.
    private fun Preferences.toPreferenciasUsuario(): PreferenciasUsuario {
        val defecto = PreferenciasUsuario()
        return PreferenciasUsuario(
            tema = enumODefecto(this[Claves.TEMA], defecto.tema),
            mostrarCompletados = this[Claves.MOSTRAR_COMPLETADOS] ?: defecto.mostrarCompletados,
            orden = enumODefecto(this[Claves.ORDEN], defecto.orden),
            filtroPrioridad = enumODefecto(this[Claves.FILTRO_PRIORIDAD], defecto.filtroPrioridad)
        )
    }

    // A diferencia de enumValueOf, no lanza excepción con un String desconocido
    private inline fun <reified E : Enum<E>> enumODefecto(valor: String?, defecto: E): E =
        enumEntries<E>().firstOrNull { it.name == valor } ?: defecto
}
