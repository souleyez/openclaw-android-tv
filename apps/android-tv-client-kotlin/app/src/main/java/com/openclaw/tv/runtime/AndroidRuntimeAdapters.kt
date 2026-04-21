package com.openclaw.tv.runtime

import android.content.Context
import android.os.PowerManager
import com.openclaw.tv.core.storage.AppDownloadStore
import com.openclaw.tv.feature.appdelivery.AppDownloadCoordinator
import com.openclaw.tv.feature.appdelivery.RuntimeManifestSnapshot
import com.openclaw.tv.feature.home.ResolvedTvHomeConfig
import com.openclaw.tv.feature.runtime.ResourceSessionCoordinator
import com.openclaw.tv.feature.runtime.ResourceSessionRepository
import com.openclaw.tv.feature.runtime.RuntimeEntitlementRepository

class RuntimeEntitlementSyncAdapter(
    private val repository: RuntimeEntitlementRepository,
) : RuntimeEntitlementSync {

    override suspend fun load(sessionToken: String, pollAfterSeconds: Int) {
        repository.load(
            sessionToken = sessionToken,
            pollAfterSeconds = pollAfterSeconds,
        )
    }

    override suspend fun clear() {
        repository.clear()
    }
}

class ResourceSessionRuntimeSyncAdapter(
    private val repository: ResourceSessionRepository,
    private val coordinator: ResourceSessionCoordinator,
) : ResourceSessionRuntimeSync {

    override suspend fun resume() {
        coordinator.resume()
    }

    override fun currentState() = coordinator.state.value

    override suspend fun request(leaseProfile: String?) {
        coordinator.request(leaseProfile = leaseProfile)
    }

    override suspend fun poll() {
        coordinator.poll()
    }

    override suspend fun renew() {
        coordinator.renew()
    }

    override suspend fun releaseBestEffort(sessionToken: String, resourceSessionId: String?) {
        repository.release(
            sessionToken = sessionToken,
            resourceSessionId = resourceSessionId,
        )
    }

    override suspend fun clear() {
        repository.clearLocalResourceSession()
        coordinator.resume()
    }
}

class AppDeliveryRuntimeSyncAdapter(
    private val coordinator: AppDownloadCoordinator,
) : AppDeliveryRuntimeSync {

    override suspend fun enqueueEligibleDownloads(
        manifest: RuntimeManifestSnapshot,
        config: ResolvedTvHomeConfig,
        deviceIsActive: Boolean,
    ) {
        val resolvedManifest = manifest.manifest ?: return
        coordinator.enqueueEligibleDownloads(
            manifest = resolvedManifest,
            backgroundDownloadEnabled = config.backgroundDownloadEnabled,
            idleDownloadOnly = config.idleDownloadOnly,
            deviceIsActive = deviceIsActive,
        )
    }
}

class SystemDeviceActivityProvider(
    private val appContext: Context,
) : DeviceActivityProvider {

    override fun isDeviceActive(): Boolean {
        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isInteractive != false
    }
}

class AppDownloadCompletionTracker(
    private val downloadStore: AppDownloadStore,
    private val coordinator: AppDownloadCoordinator,
) {

    suspend fun handleCompletedDownload(downloadId: Long) {
        val matchedDownload = downloadStore.readAll()
            .values
            .firstOrNull { it.downloadId == downloadId }
            ?: return
        val localFilePath = matchedDownload.localFilePath?.takeIf(String::isNotBlank) ?: return
        coordinator.completeDownload(
            appId = matchedDownload.appId,
            localFilePath = localFilePath,
        )
    }
}
