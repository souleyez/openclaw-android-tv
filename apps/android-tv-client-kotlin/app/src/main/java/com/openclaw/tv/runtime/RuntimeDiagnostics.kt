package com.openclaw.tv.runtime

import com.openclaw.tv.feature.runtime.ResourceSessionRuntimePhase
import java.net.URI

enum class PlatformApiEndpointMode {
    CANONICAL,
    CUSTOM,
}

data class PlatformApiEndpointSummary(
    val rawBaseUrl: String,
    val normalizedBaseUrl: String,
    val host: String?,
    val path: String,
    val mode: PlatformApiEndpointMode,
)

enum class RuntimeSyncTrigger {
    STEADY_LOOP,
}

data class RuntimeSyncCycleReport(
    val trigger: RuntimeSyncTrigger,
    val cycleSuccessful: Boolean,
    val consecutiveFailures: Int,
    val nextDelayMillis: Long,
    val queueStatus: String,
    val phase: ResourceSessionRuntimePhase,
    val hasResourceSession: Boolean,
    val errorMessage: String? = null,
)

interface RuntimeDiagnosticsReporter {
    fun onPlatformApiConfigured(summary: PlatformApiEndpointSummary) = Unit
    fun onRuntimeSyncCycle(report: RuntimeSyncCycleReport) = Unit
    fun onRuntimeSyncReset() = Unit
}

object NoOpRuntimeDiagnosticsReporter : RuntimeDiagnosticsReporter

class LoggingRuntimeDiagnosticsReporter(
    private val logInfo: (String) -> Unit,
    private val logWarning: (String) -> Unit,
) : RuntimeDiagnosticsReporter {

    private var lastFailureCount = 0
    private var lastQueueStatus: String? = null
    private var lastPhase: ResourceSessionRuntimePhase? = null
    private var lastHasResourceSession: Boolean? = null

    override fun onPlatformApiConfigured(summary: PlatformApiEndpointSummary) {
        val message = when (summary.mode) {
            PlatformApiEndpointMode.CANONICAL -> {
                "Using canonical platform API baseUrl=${summary.normalizedBaseUrl}"
            }

            PlatformApiEndpointMode.CUSTOM -> {
                "Using custom platform API baseUrl=${summary.normalizedBaseUrl}; canonicalBaseUrl=$CanonicalPlatformApiBaseUrl"
            }
        }
        when (summary.mode) {
            PlatformApiEndpointMode.CANONICAL -> logInfo(message)
            PlatformApiEndpointMode.CUSTOM,
            -> logWarning(message)
        }
    }

    override fun onRuntimeSyncCycle(report: RuntimeSyncCycleReport) {
        val stateChanged = lastQueueStatus != report.queueStatus ||
            lastPhase != report.phase ||
            lastHasResourceSession != report.hasResourceSession
        if (!report.cycleSuccessful) {
            if (lastFailureCount == 0 ||
                report.consecutiveFailures % FailureRepeatLogInterval == 0 ||
                stateChanged
            ) {
                logWarning(
                    buildString {
                        append("Runtime steady sync degraded")
                        append(" failures=${report.consecutiveFailures}")
                        append(" queueStatus=${report.queueStatus}")
                        append(" phase=${report.phase.name.lowercase()}")
                        append(" hasResourceSession=${report.hasResourceSession}")
                        append(" nextDelayMs=${report.nextDelayMillis}")
                        report.errorMessage?.takeIf(String::isNotBlank)?.let {
                            append(" error=$it")
                        }
                    },
                )
            }
        } else {
            if (lastFailureCount > 0) {
                logInfo(
                    "Runtime steady sync recovered afterFailures=$lastFailureCount queueStatus=${report.queueStatus} phase=${report.phase.name.lowercase()} hasResourceSession=${report.hasResourceSession} nextDelayMs=${report.nextDelayMillis}",
                )
            }
            if (stateChanged) {
                logInfo(
                    "Runtime steady sync state queueStatus=${report.queueStatus} phase=${report.phase.name.lowercase()} hasResourceSession=${report.hasResourceSession} nextDelayMs=${report.nextDelayMillis}",
                )
            }
        }

        lastFailureCount = report.consecutiveFailures
        lastQueueStatus = report.queueStatus
        lastPhase = report.phase
        lastHasResourceSession = report.hasResourceSession
    }

    override fun onRuntimeSyncReset() {
        lastFailureCount = 0
        lastQueueStatus = null
        lastPhase = null
        lastHasResourceSession = null
    }

    private companion object {
        const val FailureRepeatLogInterval = 5
    }
}

fun resolvePlatformApiEndpointSummary(baseUrl: String): PlatformApiEndpointSummary {
    val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
    val parsed = runCatching { URI(normalizedBaseUrl) }.getOrNull()
    val host = parsed?.host?.trim()?.lowercase()
    val path = parsed?.path.normalizePath()
    val mode = when {
        host == CanonicalPlatformApiHost && path == CanonicalPlatformApiPath -> PlatformApiEndpointMode.CANONICAL
        else -> PlatformApiEndpointMode.CUSTOM
    }
    return PlatformApiEndpointSummary(
        rawBaseUrl = baseUrl,
        normalizedBaseUrl = normalizedBaseUrl,
        host = host,
        path = path,
        mode = mode,
    )
}

private fun normalizeBaseUrl(baseUrl: String): String {
    return baseUrl.trim().trimEnd('/').ifBlank { baseUrl.trim() }
}

private fun String?.normalizePath(): String {
    val normalized = this?.trim()?.trimEnd('/').orEmpty()
    return if (normalized.isBlank()) "/" else normalized
}

private const val CanonicalPlatformApiBaseUrl = "https://oc.goods-editor.com/api"
private const val CanonicalPlatformApiHost = "oc.goods-editor.com"
private const val CanonicalPlatformApiPath = "/api"
