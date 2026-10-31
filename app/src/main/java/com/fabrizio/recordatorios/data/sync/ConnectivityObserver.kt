package com.fabrizio.recordatorios.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

// Estado de la red como Flow, para reaccionar a la reconexión
interface ConnectivityObserver {
    val estaConectado: Flow<Boolean>
    fun hayConexionAhora(): Boolean
}

class AndroidConnectivityObserver(context: Context) : ConnectivityObserver {

    private val connectivityManager =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)

    // callbackFlow convierte la API de callbacks (NetworkCallback) en un Flow.
    // El callback se registra al empezar a observar y se quita en awaitClose.
    override val estaConectado: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(false)
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            }
        }
        trySend(hayConexionAhora()) // valor inicial
        connectivityManager.registerDefaultNetworkCallback(callback)
        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged() // varios callbacks seguidos con el mismo valor = un solo evento

    override fun hayConexionAhora(): Boolean {
        val capacidades = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        return capacidades?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }
}
