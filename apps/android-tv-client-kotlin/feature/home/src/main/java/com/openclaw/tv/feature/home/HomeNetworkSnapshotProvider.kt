package com.openclaw.tv.feature.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat

internal data class HomeNetworkSnapshot(
    val isConnected: Boolean,
    val transport: String,
    val currentSsid: String?,
    val visibleNetworks: List<String>,
    val canReadWifiList: Boolean,
    val statusText: String,
) {
    companion object {
        val fallback = HomeNetworkSnapshot(
            isConnected = false,
            transport = "offline",
            currentSsid = null,
            visibleNetworks = emptyList(),
            canReadWifiList = false,
            statusText = "当前未联网",
        )
    }
}

internal class HomeNetworkSnapshotProvider(
    context: Context,
) {
    private val applicationContext = context.applicationContext

    fun snapshot(): HomeNetworkSnapshot {
        val hasNetworkStatePermission = hasPermission(Manifest.permission.ACCESS_NETWORK_STATE)
        val connectivityManager = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return HomeNetworkSnapshot.fallback
        val capabilities = if (hasNetworkStatePermission) {
            runCatching {
                val activeNetwork = connectivityManager.activeNetwork
                connectivityManager.getNetworkCapabilities(activeNetwork)
            }.getOrNull()
        } else {
            null
        }
        val isConnected = if (hasNetworkStatePermission) {
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } else {
            false
        }
        val transport = if (hasNetworkStatePermission) {
            when {
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "wifi"
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "ethernet"
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "cellular"
                else -> "offline"
            }
        } else {
            "offline"
        }

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val hasWifiPermission = hasPermission(Manifest.permission.ACCESS_WIFI_STATE)
        val hasLocationPermission = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        val currentSsid = if (wifiManager != null && hasWifiPermission) {
            runCatching { sanitizeSsid(wifiManager.connectionInfo?.ssid) }.getOrNull()
        } else {
            null
        }

        val visibleNetworks = if (wifiManager != null && hasWifiPermission && hasLocationPermission) {
            runCatching {
                wifiManager.scanResults
                    ?.mapNotNull { scanResult -> sanitizeSsid(scanResult.SSID) }
                    ?.distinct()
                    ?.take(MAX_VISIBLE_NETWORKS)
                    .orEmpty()
            }.getOrElse { emptyList() }
        } else {
            emptyList()
        }.ifEmpty {
            currentSsid?.let(::listOf).orEmpty()
        }

        return HomeNetworkSnapshot(
            isConnected = isConnected,
            transport = transport,
            currentSsid = currentSsid,
            visibleNetworks = visibleNetworks,
            canReadWifiList = hasWifiPermission && hasLocationPermission,
            statusText = buildStatusText(
                isConnected = isConnected,
                transport = transport,
                currentSsid = currentSsid,
            ),
        )
    }

    private fun buildStatusText(
        isConnected: Boolean,
        transport: String,
        currentSsid: String?,
    ): String {
        return when {
            isConnected && transport == "ethernet" -> "当前已连接有线网络"
            isConnected && currentSsid != null -> "当前已连接 Wi-Fi：$currentSsid"
            isConnected -> "当前网络已连接"
            currentSsid != null -> "当前 Wi-Fi：$currentSsid，但还未连上外网"
            else -> "当前未联网"
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(applicationContext, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun sanitizeSsid(rawSsid: String?): String? {
        if (rawSsid.isNullOrBlank()) {
            return null
        }
        val cleaned = rawSsid.replace("\"", "").trim()
        return cleaned.takeIf { it.isNotBlank() && it.equals("<unknown ssid>", ignoreCase = true).not() }
    }

    private companion object {
        private const val MAX_VISIBLE_NETWORKS = 8
    }
}
