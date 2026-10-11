package io.github.wkoller.glucosetracker.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

enum class Connection { WIFI, MOBILE, NONE }

object NetworkStatus {
    /** Wi-Fi (or a wired connection), mobile data, or no connection at all. */
    fun current(context: Context): Connection {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return Connection.NONE
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return Connection.NONE
        if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return Connection.NONE
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Connection.WIFI
            else -> Connection.MOBILE
        }
    }
}
