package com.openclaw.tv.runtime

import com.openclaw.tv.feature.runtime.ResourceSessionRuntimePhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeDiagnosticsTest {

    @Test
    fun resolves_canonical_platform_api_endpoint() {
        val summary = resolvePlatformApiEndpointSummary("https://api.souleye.cc/api/")

        assertEquals(PlatformApiEndpointMode.CANONICAL, summary.mode)
        assertEquals("api.souleye.cc", summary.host)
        assertEquals("/api", summary.path)
        assertEquals("https://api.souleye.cc/api", summary.normalizedBaseUrl)
    }

    @Test
    fun resolves_compatibility_platform_api_endpoint() {
        val summary = resolvePlatformApiEndpointSummary("https://souleye.cc/platform-api/")

        assertEquals(PlatformApiEndpointMode.COMPATIBILITY, summary.mode)
        assertEquals("souleye.cc", summary.host)
        assertEquals("/platform-api", summary.path)
    }

    @Test
    fun reporter_logs_degraded_then_recovered_runtime_sync() {
        val infoLogs = mutableListOf<String>()
        val warningLogs = mutableListOf<String>()
        val reporter = LoggingRuntimeDiagnosticsReporter(
            logInfo = { message -> infoLogs += message },
            logWarning = { message -> warningLogs += message },
        )

        reporter.onRuntimeSyncCycle(
            RuntimeSyncCycleReport(
                trigger = RuntimeSyncTrigger.STEADY_LOOP,
                cycleSuccessful = false,
                consecutiveFailures = 1,
                nextDelayMillis = 30_000L,
                queueStatus = "queued",
                phase = ResourceSessionRuntimePhase.DEGRADED,
                hasResourceSession = true,
                errorMessage = "network down",
            ),
        )
        reporter.onRuntimeSyncCycle(
            RuntimeSyncCycleReport(
                trigger = RuntimeSyncTrigger.STEADY_LOOP,
                cycleSuccessful = true,
                consecutiveFailures = 0,
                nextDelayMillis = 15_000L,
                queueStatus = "granted",
                phase = ResourceSessionRuntimePhase.ACTIVE,
                hasResourceSession = true,
                errorMessage = null,
            ),
        )

        assertEquals(1, warningLogs.size)
        assertTrue(warningLogs.single().contains("Runtime steady sync degraded"))
        assertTrue(warningLogs.single().contains("error=network down"))
        assertTrue(infoLogs.any { it.contains("Runtime steady sync recovered afterFailures=1") })
        assertTrue(infoLogs.any { it.contains("Runtime steady sync state queueStatus=granted") })
    }
}
