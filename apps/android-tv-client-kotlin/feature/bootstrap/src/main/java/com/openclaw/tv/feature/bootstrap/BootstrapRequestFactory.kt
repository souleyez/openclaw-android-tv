package com.openclaw.tv.feature.bootstrap

import android.content.Context
import android.os.Build
import com.openclaw.tv.core.network.dto.BootstrapAuthRequestDto
import com.openclaw.tv.core.storage.DeviceIdentityStore

class BootstrapRequestFactory(
    private val context: Context,
    private val deviceIdentityStore: DeviceIdentityStore,
    private val projectKey: String,
    private val principalType: String,
    private val clientVersion: String,
    private val openclawVersion: String = clientVersion,
) {

    suspend fun create(): BootstrapAuthRequestDto {
        val identity = deviceIdentityStore.ensureIdentity()
        val deviceName = listOf(Build.MANUFACTURER, Build.MODEL)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" ")
            .ifBlank { "Android TV" }
        val osVersion = Build.VERSION.RELEASE?.takeIf(String::isNotBlank)
            ?: Build.VERSION.SDK_INT.toString()

        return BootstrapAuthRequestDto(
            principalType = principalType.takeIf(String::isNotBlank),
            principalKey = identity.principalKey,
            principalLabel = identity.principalLabel.ifBlank { deviceName },
            projectKey = projectKey.takeIf(String::isNotBlank),
            deviceFingerprint = identity.installationId,
            deviceName = deviceName,
            osFamily = "android-tv",
            osVersion = osVersion,
            clientVersion = clientVersion,
            runtimeVersion = "android-${Build.VERSION.SDK_INT}",
            deviceMetadata = linkedMapOf(
                "packageName" to context.packageName,
                "brand" to Build.BRAND,
                "manufacturer" to Build.MANUFACTURER,
                "model" to Build.MODEL,
                "product" to Build.PRODUCT,
                "device" to Build.DEVICE,
                "sdkInt" to Build.VERSION.SDK_INT.toString(),
            ).filterValues(String::isNotBlank),
            openclawVersion = openclawVersion,
        )
    }
}
