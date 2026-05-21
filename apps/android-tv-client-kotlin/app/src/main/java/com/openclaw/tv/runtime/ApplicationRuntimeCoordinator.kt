package com.openclaw.tv.runtime

import com.openclaw.tv.core.storage.StoredResourceSession
import com.openclaw.tv.feature.appdelivery.RuntimeManifestSnapshot
import com.openclaw.tv.feature.bootstrap.BootstrapRuntimePhase
import com.openclaw.tv.feature.bootstrap.BootstrapRuntimeState
import com.openclaw.tv.feature.home.ConfigSource
import com.openclaw.tv.feature.home.ResolvedTvHomeConfig
import com.openclaw.tv.feature.home.TvHomeRepository
import com.openclaw.tv.feature.runtime.ResourceSessionRuntimeState
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

fun interface RuntimeConfigLoader {
    suspend fun load(): ResolvedTvHomeConfig
}

interface RuntimeEntitlementSync {
    suspend fun load(sessionToken: String, pollAfterSeconds: Int)
    suspend fun clear()
}

interface ResourceSessionRuntimeSync {
    suspend fun resume()
    fun currentState(): ResourceSessionRuntimeState
    suspend fun request(leaseProfile: String? = null)
    suspend fun poll()
    suspend fun renew()
    suspend fun releaseBestEffort(sessionToken: String, resourceSessionId: String?)
    suspend fun clear()
}

interface AppDeliveryRuntimeSync {
    suspend fun enqueueEligibleDownloads(
        manifest: RuntimeManifestSnapshot,
        config: ResolvedTvHomeConfig,
        deviceIsActive: Boolean,
    )
}

fun interface DeviceActivityProvider {
    fun isDeviceActive(): Boolean
}

class RuntimeLoopDelayPolicy(
    private val defaultDelaySeconds: Int = 15,
    private val maxDelaySeconds: Int = 300,
    private val jitterRatio: Double = 0.2,
    private val randomDouble: () -> Double = { Random.nextDouble() },
) {

    fun nextDelayMillis(
        manifestPollAfterSeconds: Int,
        resourceSessionPollAfterSeconds: Int?,
        consecutiveFailures: Int,
    ): Long {
        val baseDelaySeconds = listOfNotNull(
            manifestPollAfterSeconds.takeIf { it > 0 },
            resourceSessionPollAfterSeconds?.takeIf { it > 0 },
        ).minOrNull() ?: defaultDelaySeconds
        val scaledDelaySeconds = if (consecutiveFailures <= 0) {
            baseDelaySeconds
        } else {
            min(maxDelaySeconds, baseDelaySeconds * (1 shl min(consecutiveFailures, 4)))
        }
        val jitterFactor = 1 - jitterRatio + (randomDouble().coerceIn(0.0, 1.0) * jitterRatio * 2)
        return max(1_000L, (scaledDelaySeconds * 1_000L * jitterFactor).toLong())
    }
}

class ApplicationRuntimeCoordinator(
    private val scope: CoroutineScope,
    private val bootstrapState: Flow<BootstrapRuntimeState>,
    private val configLoader: RuntimeConfigLoader,
    private val manifestLoader: suspend (sessionToken: String, pollAfterSeconds: Int) -> RuntimeManifestSnapshot,
    private val entitlementSync: RuntimeEntitlementSync,
    private val resourceSessionSync: ResourceSessionRuntimeSync,
    private val appDeliverySync: AppDeliveryRuntimeSync? = null,
    private val deviceActivityProvider: DeviceActivityProvider = DeviceActivityProvider { true },
    private val resourceSessionLeaseProfile: String? = null,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
    private val delayFor: suspend (Long) -> Unit = { delay(it) },
    private val loopDelayPolicy: RuntimeLoopDelayPolicy = RuntimeLoopDelayPolicy(),
    private val diagnosticsReporter: RuntimeDiagnosticsReporter = NoOpRuntimeDiagnosticsReporter,
    private val deviceTelemetryReporter: DeviceTelemetryReporter = NoOpDeviceTelemetryReporter,
    private val logError: (String, Throwable) -> Unit = { _, _ -> },
) {

    private var observationJob: Job? = null
    private var steadySyncJob: Job? = null
    private var syncedSessionToken: String? = null
    private var steadySyncSessionToken: String? = null
    private var runtimeConfig: ResolvedTvHomeConfig? = null
    private var consecutiveSteadySyncFailures = 0

    fun start() {
        if (observationJob?.isActive == true) {
            return
        }
        observationJob = scope.launch {
            resourceSessionSync.resumeSafely()
            bootstrapState.collect { state ->
                syncForBootstrapState(state)
                reconcileSteadySyncLoop(state)
            }
        }
    }

    suspend fun syncForBootstrapState(state: BootstrapRuntimeState) {
        val sessionToken = state.session?.sessionToken?.trim()?.takeIf(String::isNotBlank)
        if (sessionToken == null) {
            val previousSessionToken = syncedSessionToken
            if (previousSessionToken != null) {
                syncedSessionToken = null
                resetSessionScopedRuntimeState(previousSessionToken)
            }
            return
        }

        val previousSessionToken = syncedSessionToken
        if (previousSessionToken == sessionToken) {
            return
        }
        syncedSessionToken = sessionToken

        if (previousSessionToken != null && previousSessionToken != sessionToken) {
            resetSessionScopedRuntimeState(previousSessionToken)
        }

        runtimeConfig = loadConfigSafely()
        performRuntimeSyncCycle(sessionToken)
    }

    private fun reconcileSteadySyncLoop(state: BootstrapRuntimeState) {
        val sessionToken = state.session?.sessionToken?.trim()?.takeIf(String::isNotBlank)
        val shouldRun = sessionToken != null && state.phase in SteadySyncPhases
        if (!shouldRun) {
            stopSteadySyncLoop()
            return
        }
        if (steadySyncJob?.isActive == true && steadySyncSessionToken == sessionToken) {
            return
        }
        stopSteadySyncLoop()
        steadySyncSessionToken = sessionToken
        steadySyncJob = scope.launch {
            while (isActive && steadySyncSessionToken == sessionToken) {
                val cycleSuccessful = performRuntimeSyncCycle(sessionToken)
                val resolvedConfig = runtimeConfig ?: TvHomeRepository.fallback()
                consecutiveSteadySyncFailures = if (cycleSuccessful) {
                    0
                } else {
                    consecutiveSteadySyncFailures + 1
                }
                val nextDelayMillis = loopDelayPolicy.nextDelayMillis(
                    manifestPollAfterSeconds = resolvedConfig.manifestPollAfterSeconds,
                    resourceSessionPollAfterSeconds = resolveResourceSessionPollAfterSeconds(resolvedConfig),
                    consecutiveFailures = consecutiveSteadySyncFailures,
                )
                val currentState = resourceSessionSync.currentState()
                diagnosticsReporter.onRuntimeSyncCycle(
                    RuntimeSyncCycleReport(
                        trigger = RuntimeSyncTrigger.STEADY_LOOP,
                        cycleSuccessful = cycleSuccessful,
                        consecutiveFailures = consecutiveSteadySyncFailures,
                        nextDelayMillis = nextDelayMillis,
                        queueStatus = currentState.queueStatus.normalizedQueueStatus(),
                        phase = currentState.phase,
                        hasResourceSession = currentState.resourceSession != null,
                        errorMessage = currentState.errorMessage,
                    ),
                )
                reportDeviceTelemetrySafely(
                    sessionToken = sessionToken,
                    context = DeviceTelemetryRuntimeContext(
                        cycleSuccessful = cycleSuccessful,
                        consecutiveFailures = consecutiveSteadySyncFailures,
                        nextDelayMillis = nextDelayMillis,
                        resourceSessionState = currentState,
                    ),
                )
                try {
                    delayFor(nextDelayMillis)
                } catch (error: CancellationException) {
                    throw error
                }
            }
        }
    }

    private fun stopSteadySyncLoop() {
        steadySyncJob?.cancel()
        steadySyncJob = null
        steadySyncSessionToken = null
        consecutiveSteadySyncFailures = 0
        diagnosticsReporter.onRuntimeSyncReset()
    }

    private suspend fun performRuntimeSyncCycle(sessionToken: String): Boolean {
        val config = runtimeConfig ?: loadConfigSafely().also { runtimeConfig = it }
        var cycleSuccessful = true
        val manifestSnapshot = runCatching {
            manifestLoader(sessionToken, config.manifestPollAfterSeconds)
        }.onFailure { error ->
            error.rethrowIfCancellation()
            cycleSuccessful = false
            logError("Failed to refresh runtime manifest", error)
        }.getOrNull()

        if (!entitlementSync.loadSafely(
                sessionToken = sessionToken,
                pollAfterSeconds = resolveEntitlementPollAfterSeconds(config),
            )
        ) {
            cycleSuccessful = false
        }
        if (!syncResourceSession(config)) {
            cycleSuccessful = false
        }
        if (manifestSnapshot != null) {
            val appDeliverySuccessful = appDeliverySync?.enqueueSafely(
                    manifest = manifestSnapshot,
                    config = config,
                    deviceIsActive = deviceActivityProvider.isDeviceActive(),
                )
                ?: true
            if (!appDeliverySuccessful) {
                cycleSuccessful = false
            }
        }
        return cycleSuccessful
    }

    private suspend fun syncResourceSession(config: ResolvedTvHomeConfig): Boolean {
        val currentState = resourceSessionSync.currentState()
        val queueStatus = currentState.queueStatus.normalizedQueueStatus()
        val resourceSession = currentState.resourceSession
        return when {
            resourceSession == null ||
                queueStatus in RequestableQueueStatuses ||
                isExpiredResourceSession(resourceSession, queueStatus) -> {
                resourceSessionSync.requestSafely(resourceSessionLeaseProfile)
            }

            shouldRenewResourceSession(
                resourceSession = resourceSession,
                queueStatus = queueStatus,
                resourceSessionPollAfterSeconds = config.resourceSessionPollAfterSeconds,
            ) -> {
                resourceSessionSync.renewSafely()
            }

            currentState.nextPollAfterSeconds != null || queueStatus in PollableQueueStatuses -> {
                resourceSessionSync.pollSafely()
            }

            else -> true
        }
    }

    private fun resolveResourceSessionPollAfterSeconds(config: ResolvedTvHomeConfig): Int? {
        return resourceSessionSync.currentState().nextPollAfterSeconds
            ?.takeIf { it > 0 }
            ?: config.resourceSessionPollAfterSeconds.takeIf { it > 0 }
    }

    private fun resolveEntitlementPollAfterSeconds(config: ResolvedTvHomeConfig): Int {
        return max(
            DefaultEntitlementPollAfterSeconds,
            config.manifestPollAfterSeconds.coerceAtLeast(1),
        )
    }

    private fun shouldRenewResourceSession(
        resourceSession: StoredResourceSession,
        queueStatus: String,
        resourceSessionPollAfterSeconds: Int,
    ): Boolean {
        if (queueStatus != "granted") {
            return false
        }
        val expiresAtEpochMs = parseUtcIsoToEpochMs(resourceSession.expiresAt) ?: return false
        val renewWindowMs = max(30_000L, resourceSessionPollAfterSeconds.coerceAtLeast(1) * 2_000L)
        return expiresAtEpochMs - nowEpochMs() <= renewWindowMs
    }

    private fun isExpiredResourceSession(
        resourceSession: StoredResourceSession,
        queueStatus: String,
    ): Boolean {
        if (queueStatus !in ExpirableQueueStatuses) {
            return false
        }
        val expiresAtEpochMs = parseUtcIsoToEpochMs(resourceSession.expiresAt) ?: return false
        return expiresAtEpochMs <= nowEpochMs()
    }

    private suspend fun loadConfigSafely(): ResolvedTvHomeConfig {
        val previousConfig = runtimeConfig
        return runCatching {
            configLoader.load()
        }.getOrElse { error ->
            error.rethrowIfCancellation()
            logError("Failed to load TV home config", error)
            previousConfig ?: TvHomeRepository.fallback()
        }.let { resolvedConfig ->
            if (resolvedConfig.source == ConfigSource.FALLBACK && previousConfig != null) {
                previousConfig
            } else {
                resolvedConfig
            }
        }
    }

    private suspend fun resetSessionScopedRuntimeState(previousSessionToken: String) {
        releasePreviousResourceSessionSafely(previousSessionToken)
        entitlementSync.clearSafely()
        resourceSessionSync.clearSafely()
    }

    private suspend fun releasePreviousResourceSessionSafely(previousSessionToken: String) {
        val currentState = resourceSessionSync.currentState()
        val resourceSessionId = currentState.resourceSession
            ?.resourceSessionId
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: return
        val queueStatus = currentState.resourceSession?.queueStatus
            ?.normalizedQueueStatus()
            ?: currentState.queueStatus.normalizedQueueStatus()
        if (queueStatus in NonReleasableQueueStatuses) {
            return
        }
        runCatching {
            resourceSessionSync.releaseBestEffort(
                sessionToken = previousSessionToken,
                resourceSessionId = resourceSessionId,
            )
        }.onFailure { error ->
            error.rethrowIfCancellation()
            logError("Failed to release previous resource-session state", error)
        }
    }

    private suspend fun RuntimeEntitlementSync.loadSafely(
        sessionToken: String,
        pollAfterSeconds: Int,
    ): Boolean {
        return runCatching {
            load(
                sessionToken = sessionToken,
                pollAfterSeconds = pollAfterSeconds,
            )
        }.onFailure { error ->
            error.rethrowIfCancellation()
            logError("Failed to refresh entitlement summary", error)
        }.isSuccess
    }

    private suspend fun RuntimeEntitlementSync.clearSafely() {
        runCatching {
            clear()
        }.onFailure { error ->
            error.rethrowIfCancellation()
            logError("Failed to clear entitlement summary", error)
        }
    }

    private suspend fun ResourceSessionRuntimeSync.resumeSafely(): Boolean {
        return runCatching {
            resume()
        }.onFailure { error ->
            error.rethrowIfCancellation()
            logError("Failed to resume resource-session runtime", error)
        }.isSuccess
    }

    private suspend fun ResourceSessionRuntimeSync.requestSafely(leaseProfile: String?): Boolean {
        return runCatching {
            request(leaseProfile)
        }.onFailure { error ->
            error.rethrowIfCancellation()
            logError("Failed to request resource-session state", error)
        }.isSuccess
    }

    private suspend fun ResourceSessionRuntimeSync.pollSafely(): Boolean {
        return runCatching {
            poll()
        }.onFailure { error ->
            error.rethrowIfCancellation()
            logError("Failed to poll resource-session state", error)
        }.isSuccess
    }

    private suspend fun ResourceSessionRuntimeSync.renewSafely(): Boolean {
        return runCatching {
            renew()
        }.onFailure { error ->
            error.rethrowIfCancellation()
            logError("Failed to renew resource-session state", error)
        }.isSuccess
    }

    private suspend fun ResourceSessionRuntimeSync.clearSafely() {
        runCatching {
            clear()
        }.onFailure { error ->
            error.rethrowIfCancellation()
            logError("Failed to clear resource-session state", error)
        }
    }

    private suspend fun AppDeliveryRuntimeSync.enqueueSafely(
        manifest: RuntimeManifestSnapshot,
        config: ResolvedTvHomeConfig,
        deviceIsActive: Boolean,
    ): Boolean {
        return runCatching {
            enqueueEligibleDownloads(
                manifest = manifest,
                config = config,
                deviceIsActive = deviceIsActive,
            )
        }.onFailure { error ->
            error.rethrowIfCancellation()
            logError("Failed to enqueue runtime app downloads", error)
        }.isSuccess
    }

    private suspend fun reportDeviceTelemetrySafely(
        sessionToken: String,
        context: DeviceTelemetryRuntimeContext,
    ) {
        runCatching {
            deviceTelemetryReporter.maybeReport(sessionToken, context)
        }.onFailure { error ->
            error.rethrowIfCancellation()
            logError("Failed to report device telemetry", error)
        }
    }

    private fun String.normalizedQueueStatus(): String {
        return trim().lowercase().ifBlank { "not_requested" }
    }

    private fun parseUtcIsoToEpochMs(value: String?): Long? {
        val normalizedValue = value?.trim()?.takeIf(String::isNotBlank) ?: return null
        return synchronized(IsoUtcFormat) {
            runCatching {
                IsoUtcFormat.parse(normalizedValue)?.time
            }.getOrNull()
        }
    }

    private companion object {
        val SteadySyncPhases = setOf(
            BootstrapRuntimePhase.READY,
            BootstrapRuntimePhase.DEGRADED,
        )
        val RequestableQueueStatuses = setOf(
            "not_requested",
            "released",
            "rejected",
            "expired",
        )
        val PollableQueueStatuses = setOf(
            "queued",
            "allocating",
            "granted",
            "degraded",
        )
        val ExpirableQueueStatuses = setOf(
            "granted",
            "degraded",
        )
        val NonReleasableQueueStatuses = setOf(
            "not_requested",
            "released",
            "rejected",
            "expired",
        )
        const val DefaultEntitlementPollAfterSeconds = 300
        val IsoUtcFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
}

private fun Throwable.rethrowIfCancellation() {
    if (this is CancellationException) {
        throw this
    }
}
