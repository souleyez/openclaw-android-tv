package com.openclaw.tv.runtime

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Debug
import com.openclaw.tv.BuildConfig
import com.openclaw.tv.core.network.PlatformApi
import com.openclaw.tv.core.network.dto.DeviceTelemetryRequestDto
import com.openclaw.tv.feature.runtime.ResourceSessionRuntimeState
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class DeviceTelemetryRuntimeContext(
    val cycleSuccessful: Boolean,
    val consecutiveFailures: Int,
    val nextDelayMillis: Long,
    val resourceSessionState: ResourceSessionRuntimeState,
)

fun interface DeviceTelemetryReporter {
    suspend fun maybeReport(sessionToken: String, context: DeviceTelemetryRuntimeContext)
}

object NoOpDeviceTelemetryReporter : DeviceTelemetryReporter {
    override suspend fun maybeReport(sessionToken: String, context: DeviceTelemetryRuntimeContext) = Unit
}

class PlatformDeviceTelemetryReporter(
    private val platformApi: PlatformApi,
    private val sampler: DeviceTelemetrySampler,
    private val minIntervalMillis: Long = DefaultDeviceTelemetryIntervalMillis,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) : DeviceTelemetryReporter {

    private var lastReportedAtMs: Long? = null

    override suspend fun maybeReport(sessionToken: String, context: DeviceTelemetryRuntimeContext) {
        val now = nowEpochMs()
        val lastReported = lastReportedAtMs
        if (lastReported != null && now - lastReported < minIntervalMillis) {
            return
        }

        platformApi.postDeviceTelemetry(
            sessionToken = sessionToken,
            request = sampler.sample(context, now),
        )
        lastReportedAtMs = now
    }
}

interface DeviceTelemetrySampler {
    fun sample(context: DeviceTelemetryRuntimeContext, nowEpochMs: Long): DeviceTelemetryRequestDto
}

class AndroidDeviceTelemetrySampler(
    context: Context,
) : DeviceTelemetrySampler {

    private val applicationContext = context.applicationContext

    override fun sample(
        context: DeviceTelemetryRuntimeContext,
        nowEpochMs: Long,
    ): DeviceTelemetryRequestDto {
        return DeviceTelemetryRequestDto(
            capturedAt = formatUtcIso(nowEpochMs),
            appVersion = BuildConfig.VERSION_NAME,
            openclawVersion = BuildConfig.VERSION_NAME,
            runtimeVersion = "android-${Build.VERSION.SDK_INT}",
            foregroundState = foregroundState(),
            castState = "unknown",
            network = networkSnapshot(),
            memory = memorySnapshot(),
            storage = storageSnapshot(),
            resourceSession = resourceSessionSnapshot(context),
        )
    }

    private fun foregroundState(): String {
        val state = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(state)
        return when (state.importance) {
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "foreground"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "visible"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> "service"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED -> "cached"
            else -> "background"
        }
    }

    private fun memorySnapshot(): Map<String, String> {
        val processMemory = Debug.MemoryInfo()
        Debug.getMemoryInfo(processMemory)
        val activityManager = applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val systemMemory = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(systemMemory)
        return buildMap {
            put("appPssKb", processMemory.totalPss.toString())
            put("appPrivateDirtyKb", processMemory.totalPrivateDirty.toString())
            put("systemAvailBytes", systemMemory.availMem.toString())
            put("systemTotalBytes", systemMemory.totalMem.toString())
            put("systemThresholdBytes", systemMemory.threshold.toString())
            put("systemLowMemory", systemMemory.lowMemory.toString())
        }
    }

    private fun storageSnapshot(): Map<String, String> {
        val dataDir = applicationContext.filesDir
        val cacheDir = applicationContext.cacheDir
        return buildMap {
            put("dataUsableBytes", dataDir.usableSpace.toString())
            put("dataTotalBytes", dataDir.totalSpace.toString())
            put("cacheUsableBytes", cacheDir.usableSpace.toString())
        }
    }

    @Suppress("DEPRECATION")
    private fun networkSnapshot(): Map<String, String> {
        val connectivityManager =
            applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return emptyMap()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            val networkInfo = connectivityManager.activeNetworkInfo
            return mapOf(
                "connected" to (networkInfo?.isConnected == true).toString(),
                "transport" to (networkInfo?.typeName ?: "unknown").lowercase(Locale.US),
            )
        }

        val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        return mapOf(
            "connected" to (capabilities != null).toString(),
            "transport" to when {
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "wifi"
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "ethernet"
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "cellular"
                else -> "unknown"
            },
        )
    }

    private fun resourceSessionSnapshot(context: DeviceTelemetryRuntimeContext): Map<String, String> {
        val state = context.resourceSessionState
        val session = state.resourceSession
        return buildMap {
            put("cycleSuccessful", context.cycleSuccessful.toString())
            put("consecutiveFailures", context.consecutiveFailures.toString())
            put("nextDelayMillis", context.nextDelayMillis.toString())
            put("queueStatus", state.queueStatus.trim().lowercase().ifBlank { "not_requested" })
            put("phase", state.phase.name.lowercase(Locale.US))
            put("hasResourceSession", (session != null).toString())
            state.nextPollAfterSeconds?.let { put("nextPollAfterSeconds", it.toString()) }
            session?.resourceSessionId?.takeIf(String::isNotBlank)?.let { put("resourceSessionId", it) }
            session?.expiresAt?.takeIf(String::isNotBlank)?.let { put("expiresAt", it) }
        }
    }

    private companion object {
        val IsoUtcFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        fun formatUtcIso(epochMs: Long): String = synchronized(IsoUtcFormat) {
            IsoUtcFormat.format(epochMs)
        }
    }
}

const val DefaultDeviceTelemetryIntervalMillis = 5 * 60 * 1_000L
