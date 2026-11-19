package com.example.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class DeviceNetworkState(
    val isWifiConnected: Boolean = false,
    val isCellularConnected: Boolean = false,
    val isVpnTransportActive: Boolean = false
)

class NetworkStatusMonitor(context: Context) {

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val wifiNetworks = mutableSetOf<Network>()
    private val cellularNetworks = mutableSetOf<Network>()
    private val vpnNetworks = mutableSetOf<Network>()

    private val _networkState = MutableStateFlow(readInitialState())
    val networkState: StateFlow<DeviceNetworkState> = _networkState.asStateFlow()

    private var registered = false

    private val wifiCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            synchronized(wifiNetworks) { wifiNetworks.add(network) }
            emitState()
        }

        override fun onLost(network: Network) {
            synchronized(wifiNetworks) { wifiNetworks.remove(network) }
            emitState()
        }
    }

    private val cellularCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            synchronized(cellularNetworks) { cellularNetworks.add(network) }
            emitState()
        }

        override fun onLost(network: Network) {
            synchronized(cellularNetworks) { cellularNetworks.remove(network) }
            emitState()
        }
    }

    private val vpnCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            synchronized(vpnNetworks) { vpnNetworks.add(network) }
            emitState()
        }

        override fun onLost(network: Network) {
            synchronized(vpnNetworks) { vpnNetworks.remove(network) }
            emitState()
        }
    }

    fun start() {
        if (registered) return
        registered = true
        _networkState.value = readInitialState()

        val wifiReq = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()

        val cellularReq = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()

        val vpnReq = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()

        runCatching {
            connectivityManager.registerNetworkCallback(wifiReq, wifiCallback)
            connectivityManager.registerNetworkCallback(cellularReq, cellularCallback)
            connectivityManager.registerNetworkCallback(vpnReq, vpnCallback)
        }
    }

    fun stop() {
        if (!registered) return
        registered = false
        runCatching { connectivityManager.unregisterNetworkCallback(wifiCallback) }
        runCatching { connectivityManager.unregisterNetworkCallback(cellularCallback) }
        runCatching { connectivityManager.unregisterNetworkCallback(vpnCallback) }
    }

    @Suppress("DEPRECATION")
    private fun readInitialState(): DeviceNetworkState {
        var wifi = false
        var cellular = false
        var vpn = false
        runCatching {
            for (network in connectivityManager.allNetworks) {
                val caps = connectivityManager.getNetworkCapabilities(network) ?: continue
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    vpn = true
                } else {
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) wifi = true
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) cellular = true
                }
            }
        }
        return DeviceNetworkState(
            isWifiConnected = wifi,
            isCellularConnected = cellular,
            isVpnTransportActive = vpn
        )
    }

    private fun emitState() {
        val wifi = synchronized(wifiNetworks) { wifiNetworks.isNotEmpty() }
        val cellular = synchronized(cellularNetworks) { cellularNetworks.isNotEmpty() }
        val vpn = synchronized(vpnNetworks) { vpnNetworks.isNotEmpty() }
        _networkState.update {
            it.copy(
                isWifiConnected = wifi,
                isCellularConnected = cellular,
                isVpnTransportActive = vpn
            )
        }
    }
}
