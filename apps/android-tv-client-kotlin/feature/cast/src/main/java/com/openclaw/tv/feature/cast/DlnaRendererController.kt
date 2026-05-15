package com.openclaw.tv.feature.cast

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.charset.StandardCharsets
import java.util.UUID

class DlnaRendererController(
    context: Context,
    private val deviceName: String = resolveDeviceName(context),
    private val onMediaRequest: (DlnaMediaRequest) -> Unit = {},
) {
    private val applicationContext = context.applicationContext
    private val multicastLock = DlnaMulticastLock(applicationContext)
    private val _state = MutableStateFlow(
        DlnaRendererState(
            displayName = deviceName,
            isRunning = false,
        ),
    )
    val state: StateFlow<DlnaRendererState> = _state.asStateFlow()
    private var renderer: LightweightDlnaRenderer? = null

    fun start() {
        if (renderer != null) {
            return
        }
        multicastLock.acquire()
        val activeRenderer = LightweightDlnaRenderer(
            config = DlnaRendererConfig(
                uuid = resolveStableUuid(applicationContext),
                deviceName = deviceName,
                modelName = Build.MODEL?.takeIf { it.isNotBlank() } ?: "RS AITV",
            ),
            onStateChanged = { state ->
                _state.value = state
                if (!state.isRunning && state.errorMessage != null) {
                    renderer = null
                    multicastLock.release()
                }
            },
            onMediaRequest = onMediaRequest,
        )
        renderer = activeRenderer
        activeRenderer.start()
        if (!_state.value.isRunning) {
            renderer = null
        }
    }

    fun stop() {
        renderer?.stop()
        renderer = null
        multicastLock.release()
        _state.value = DlnaRendererState(
            displayName = deviceName,
            isRunning = false,
        )
    }

    private class DlnaMulticastLock(context: Context) {
        private val lock = (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
            ?.createMulticastLock("OpenClawDlnaRenderer")
            ?.apply { setReferenceCounted(false) }

        fun acquire() {
            runCatching {
                val activeLock = lock ?: return
                if (!activeLock.isHeld) {
                    activeLock.acquire()
                }
            }
        }

        fun release() {
            runCatching {
                val activeLock = lock ?: return
                if (activeLock.isHeld) {
                    activeLock.release()
                }
            }
        }
    }

    companion object {
        fun resolveDeviceName(context: Context): String {
            val configuredName = runCatching {
                Settings.Global.getString(context.contentResolver, "device_name")
            }.getOrNull()
            return configuredName
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: Build.MODEL
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                ?: "RS AITV"
        }

        private fun resolveStableUuid(context: Context): String {
            val androidId = runCatching {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            }.getOrNull()
            val seed = buildString {
                append("openclaw-tv:")
                append(androidId?.takeIf { it.isNotBlank() } ?: "unknown-device")
                append(':')
                append(Build.MODEL?.takeIf { it.isNotBlank() } ?: "unknown-model")
            }
            return UUID.nameUUIDFromBytes(seed.toByteArray(StandardCharsets.UTF_8)).toString()
        }
    }
}
