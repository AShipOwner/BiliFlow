package com.biliflow.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object NetworkMonitor {
    private val _profile = MutableStateFlow(NetworkProfile())
    val profile: StateFlow<NetworkProfile> = _profile.asStateFlow()

    fun init(context: Context) {
        val manager = context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        _profile.value = readProfile(manager)
        runCatching {
            manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(
                    network: Network,
                    capabilities: NetworkCapabilities
                ) {
                    _profile.value = profileFrom(capabilities)
                }

                override fun onLost(network: Network) {
                    _profile.value = NetworkProfile(online = false, wifi = false)
                }
            })
        }
    }

    private fun readProfile(manager: ConnectivityManager): NetworkProfile {
        val network = manager.activeNetwork ?: return NetworkProfile(online = false, wifi = false)
        val capabilities = manager.getNetworkCapabilities(network)
            ?: return NetworkProfile(online = false, wifi = false)
        return profileFrom(capabilities)
    }

    private fun profileFrom(capabilities: NetworkCapabilities): NetworkProfile {
        val wifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        val cellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        val online = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val metered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        return NetworkProfile(
            online = online,
            wifi = wifi,
            cellular = cellular,
            downKbps = capabilities.linkDownstreamBandwidthKbps,
            metered = metered
        )
    }
}
