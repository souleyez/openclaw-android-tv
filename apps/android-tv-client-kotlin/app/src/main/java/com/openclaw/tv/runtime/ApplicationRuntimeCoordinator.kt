package com.openclaw.tv.runtime

import com.openclaw.tv.feature.appdelivery.RuntimeManifestSnapshot
import com.openclaw.tv.feature.bootstrap.BootstrapRuntimeState
import com.openclaw.tv.feature.home.ResolvedTvHomeConfig
import com.openclaw.tv.feature.home.TvHomeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

fun interface RuntimeConfigLoader {
    suspend fun load(): ResolvedTvHomeConfig
}

interface RuntimeEntitlementSync {
    suspend fun load(sessionToken: String)
    suspend fun clear()
}

interface ResourceSessionRuntimeSync {
    suspend fun resume()
    suspend fun hasStoredSession(): Boolean
    suspend fun poll()
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

class ApplicationRuntimeCoordinator(
    private val scope: CoroutineScope,
    private val bootstrapState: Flow<BootstrapRuntimeState>,
    private val configLoader: RuntimeConfigLoader,
    private val manifestLoader: suspend (sessionToken: String, pollAfterSeconds: Int) -> RuntimeManifestSnapshot,
    private val entitlementSync: RuntimeEntitlementSync,
    private val resourceSessionSync: ResourceSessionRuntimeSync,
    private val appDeliverySync: AppDeliveryRuntimeSync? = null,
    private val deviceActivityProvider: DeviceActivityProvider = DeviceActivityProvider { true },
    private val logError: (String, Throwable) -> Unit = { _, _ -> },
) {

    private var observationJob: Job? = null
    private var syncedSessionToken: String? = null
    private var runtimeConfig: ResolvedTvHomeConfig? = null

    fun start() {
        if (observationJob?.isActive == true) {
            return
        }
        observationJob = scope.launch {
            resourceSessionSync.resumeSafely()
            runtimeConfig = loadConfigSafely()
            bootstrapState.collect { state ->
                syncForBootstrapState(state)
            }
        }
    }

    suspend fun syncForBootstrapState(state: BootstrapRuntimeState) {
        val sessionToken = state.session?.sessionToken?.trim()?.takeIf(String::isNotBlank) ?: return
        val previousSessionToken = syncedSessionToken
        if (previousSessionToken == sessionToken) {
            return
        }
        syncedSessionToken = sessionToken

        if (previousSessionToken != null && previousSessionToken != sessionToken) {
            entitlementSync.clearSafely()
            resourceSessionSync.clearSafely()
        }

        val config = runtimeConfig ?: loadConfigSafely().also { runtimeConfig = it }
        val manifestSnapshot = runCatching {
            manifestLoader(sessionToken, config.manifestPollAfterSeconds)
        }.getOrElse { error ->
            logError("Failed to refresh runtime manifest", error)
            return
        }

        entitlementSync.loadSafely(sessionToken)
        resourceSessionSync.resumeSafely()
        if (resourceSessionSync.hasStoredSessionSafely()) {
            resourceSessionSync.pollSafely()
        }
        appDeliverySync?.enqueueSafely(
            manifest = manifestSnapshot,
            config = config,
            deviceIsActive = deviceActivityProvider.isDeviceActive(),
        )
    }

    private suspend fun loadConfigSafely(): ResolvedTvHomeConfig {
        return runCatching {
            configLoader.load()
        }.getOrElse { error ->
            logError("Failed to load TV home config", error)
            TvHomeRepository.fallback()
        }
    }

    private suspend fun RuntimeEntitlementSync.loadSafely(sessionToken: String) {
        runCatching {
            load(sessionToken)
        }.onFailure { error ->
            logError("Failed to refresh entitlement summary", error)
        }
    }

    private suspend fun RuntimeEntitlementSync.clearSafely() {
        runCatching {
            clear()
        }.onFailure { error ->
            logError("Failed to clear entitlement summary", error)
        }
    }

    private suspend fun ResourceSessionRuntimeSync.resumeSafely() {
        runCatching {
            resume()
        }.onFailure { error ->
            logError("Failed to resume resource-session runtime", error)
        }
    }

    private suspend fun ResourceSessionRuntimeSync.hasStoredSessionSafely(): Boolean {
        return runCatching {
            hasStoredSession()
        }.getOrElse { error ->
            logError("Failed to inspect stored resource-session state", error)
            false
        }
    }

    private suspend fun ResourceSessionRuntimeSync.pollSafely() {
        runCatching {
            poll()
        }.onFailure { error ->
            logError("Failed to poll resource-session state", error)
        }
    }

    private suspend fun ResourceSessionRuntimeSync.clearSafely() {
        runCatching {
            clear()
        }.onFailure { error ->
            logError("Failed to clear resource-session state", error)
        }
    }

    private suspend fun AppDeliveryRuntimeSync.enqueueSafely(
        manifest: RuntimeManifestSnapshot,
        config: ResolvedTvHomeConfig,
        deviceIsActive: Boolean,
    ) {
        runCatching {
            enqueueEligibleDownloads(
                manifest = manifest,
                config = config,
                deviceIsActive = deviceIsActive,
            )
        }.onFailure { error ->
            logError("Failed to enqueue runtime app downloads", error)
        }
    }
}
