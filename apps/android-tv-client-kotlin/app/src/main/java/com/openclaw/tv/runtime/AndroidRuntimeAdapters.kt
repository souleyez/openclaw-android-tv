package com.openclaw.tv.runtime

import android.content.Context
import android.os.PowerManager
import com.openclaw.tv.core.storage.AppDownloadStore
import com.openclaw.tv.core.storage.StoredAppDownloadState
import com.openclaw.tv.feature.appdelivery.AppDownloadCoordinator
import com.openclaw.tv.feature.appdelivery.RuntimeManifestSnapshot
import com.openclaw.tv.feature.appdelivery.TrackedAppDownloadStatus
import com.openclaw.tv.feature.appdelivery.TrackedAppDownloadStatusResolver
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
    private val statusResolver: TrackedAppDownloadStatusResolver,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {

    suspend fun handleCompletedDownload(downloadId: Long) {
        val matchedDownload = downloadStore.readAll()
            .values
            .firstOrNull { it.downloadId == downloadId }
            ?: return
        when (val resolvedStatus = statusResolver.resolve(downloadId)) {
            is TrackedAppDownloadStatus.Successful -> {
                val localFilePath = resolvedStatus.localFilePath
                    ?.trim()
                    ?.takeIf(String::isNotBlank)
                    ?: matchedDownload.localFilePath?.trim()?.takeIf(String::isNotBlank)
                if (localFilePath.isNullOrBlank()) {
                    markFailed(
                        download = matchedDownload,
                        message = "Downloaded file is unavailable",
                    )
                } else {
                    coordinator.completeDownload(
                        appId = matchedDownload.appId,
                        localFilePath = localFilePath,
                    )
                }
            }

            is TrackedAppDownloadStatus.Failed -> {
                markFailed(
                    download = matchedDownload,
                    message = resolvedStatus.message,
                )
            }

            TrackedAppDownloadStatus.Missing -> {
                markFailed(
                    download = matchedDownload,
                    message = "DownloadManager record not found",
                )
            }

            is TrackedAppDownloadStatus.Pending,
            is TrackedAppDownloadStatus.Running,
            is TrackedAppDownloadStatus.Paused,
            -> Unit
        }
    }

    private suspend fun markFailed(
        download: StoredAppDownloadState,
        message: String,
    ) {
        downloadStore.upsert(
            download.copy(
                status = "failed",
                downloadedBytes = null,
                totalBytes = null,
                downloadDetailMessage = null,
                errorMessage = message,
                updatedAtEpochMs = nowEpochMs(),
            ),
        )
    }
}
