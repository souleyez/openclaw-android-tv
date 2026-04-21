package com.openclaw.tv.feature.appdelivery

import com.openclaw.tv.core.storage.AppDownloadStore
import com.openclaw.tv.core.storage.StoredAppDownloadState
import com.openclaw.tv.core.storage.StoredRuntimeApp
import com.openclaw.tv.core.storage.StoredRuntimeManifest
import kotlinx.coroutines.CancellationException

data class AppDownloadRequest(
    val appId: String,
    val title: String,
    val packageName: String,
    val versionCode: Long,
    val versionName: String,
    val downloadUrl: String,
    val sha256: String,
)

data class QueuedAppDownload(
    val downloadId: Long? = null,
    val localFilePath: String? = null,
)

interface AppDownloadEnqueuer {
    suspend fun enqueue(request: AppDownloadRequest): QueuedAppDownload
}

interface AppChecksumVerifier {
    suspend fun sha256(localFilePath: String): String
}

class AppDownloadCoordinator(
    private val downloadStore: AppDownloadStore,
    private val enqueuer: AppDownloadEnqueuer,
    private val checksumVerifier: AppChecksumVerifier,
    private val installedPackageChecker: InstalledPackageChecker? = null,
    private val idlePolicy: IdleDownloadPolicy = IdleDownloadPolicy(),
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {

    suspend fun enqueueEligibleDownloads(
        manifest: StoredRuntimeManifest,
        backgroundDownloadEnabled: Boolean,
        idleDownloadOnly: Boolean,
        deviceIsActive: Boolean,
    ): List<StoredAppDownloadState> {
        if (!backgroundDownloadEnabled) {
            return emptyList()
        }

        val enqueuedStates = mutableListOf<StoredAppDownloadState>()
        manifest.apps.forEach { app ->
            if (!shouldAutoDownload(app)) {
                return@forEach
            }
            if (!idlePolicy.canEnqueue(app.preloadPolicy, idleDownloadOnly, deviceIsActive)) {
                return@forEach
            }
            if (isPackageInstalled(app.packageName)) {
                return@forEach
            }
            if (shouldSkipExistingDownload(app)) {
                return@forEach
            }

            val queuedDownload = enqueuer.enqueue(
                AppDownloadRequest(
                    appId = app.appId,
                    title = app.title,
                    packageName = app.packageName,
                    versionCode = app.versionCode,
                    versionName = app.versionName,
                    downloadUrl = app.downloadUrl,
                    sha256 = app.sha256,
                ),
            )
            val queuedState = StoredAppDownloadState(
                appId = app.appId,
                title = app.title,
                packageName = app.packageName,
                versionCode = app.versionCode,
                versionName = app.versionName,
                downloadUrl = app.downloadUrl,
                sha256 = app.sha256,
                status = "queued",
                downloadId = queuedDownload.downloadId,
                localFilePath = queuedDownload.localFilePath,
                downloadedBytes = null,
                totalBytes = null,
                downloadDetailMessage = null,
                errorMessage = null,
                updatedAtEpochMs = nowEpochMs(),
            )
            downloadStore.upsert(queuedState)
            enqueuedStates += queuedState
        }
        return enqueuedStates
    }

    suspend fun completeDownload(
        appId: String,
        localFilePath: String,
    ): StoredAppDownloadState? {
        val currentState = downloadStore.read(appId) ?: return null
        val downloadedState = currentState.copy(
            status = "downloaded",
            localFilePath = localFilePath,
            downloadedBytes = null,
            totalBytes = null,
            downloadDetailMessage = null,
            errorMessage = null,
            updatedAtEpochMs = nowEpochMs(),
        )
        downloadStore.upsert(downloadedState)

        val verifyingState = downloadedState.copy(
            status = "verifying",
            downloadedBytes = null,
            totalBytes = null,
            downloadDetailMessage = null,
            updatedAtEpochMs = nowEpochMs(),
        )
        downloadStore.upsert(verifyingState)

        return try {
            val actualSha256 = checksumVerifier.sha256(localFilePath)
            if (!actualSha256.equals(verifyingState.sha256, ignoreCase = true)) {
                val failedState = verifyingState.copy(
                    status = "failed",
                    downloadedBytes = null,
                    totalBytes = null,
                    downloadDetailMessage = null,
                    errorMessage = "SHA-256 mismatch",
                    updatedAtEpochMs = nowEpochMs(),
                )
                downloadStore.upsert(failedState)
                failedState
            } else {
                val readyState = verifyingState.copy(
                    status = "ready_to_install",
                    downloadedBytes = null,
                    totalBytes = null,
                    downloadDetailMessage = null,
                    errorMessage = null,
                    updatedAtEpochMs = nowEpochMs(),
                )
                downloadStore.upsert(readyState)
                readyState
            }
        } catch (error: Exception) {
            error.rethrowIfCancellation()
            val failedState = verifyingState.copy(
                status = "failed",
                downloadedBytes = null,
                totalBytes = null,
                downloadDetailMessage = null,
                errorMessage = error.message ?: "Checksum verification failed",
                updatedAtEpochMs = nowEpochMs(),
            )
            downloadStore.upsert(failedState)
            failedState
        }
    }

    suspend fun refreshTrackedDownloads(
        statusResolver: TrackedAppDownloadStatusResolver,
    ): Int {
        var updatedCount = 0
        val trackedDownloads = downloadStore.readAll()
            .values
            .filter { it.status.trim().lowercase() in TrackableStatuses }
        for (download in trackedDownloads) {
            val downloadId = download.downloadId?.takeIf { it > 0L } ?: continue
            when (val status = statusResolver.resolve(downloadId)) {
                is TrackedAppDownloadStatus.Pending -> {
                    val nextState = download.copy(
                        status = "queued",
                        downloadedBytes = status.downloadedBytes,
                        totalBytes = status.totalBytes,
                        downloadDetailMessage = null,
                        errorMessage = null,
                        updatedAtEpochMs = nowEpochMs(),
                    )
                    if (nextState != download) {
                        downloadStore.upsert(nextState)
                        updatedCount += 1
                    }
                }

                is TrackedAppDownloadStatus.Running -> {
                    val nextState = download.copy(
                        status = "downloading",
                        downloadedBytes = status.downloadedBytes,
                        totalBytes = status.totalBytes,
                        downloadDetailMessage = null,
                        errorMessage = null,
                        updatedAtEpochMs = nowEpochMs(),
                    )
                    if (nextState != download) {
                        downloadStore.upsert(nextState)
                        updatedCount += 1
                    }
                }

                is TrackedAppDownloadStatus.Paused -> {
                    val nextState = download.copy(
                        status = "paused",
                        downloadedBytes = status.downloadedBytes,
                        totalBytes = status.totalBytes,
                        downloadDetailMessage = status.message,
                        errorMessage = null,
                        updatedAtEpochMs = nowEpochMs(),
                    )
                    if (nextState != download) {
                        downloadStore.upsert(nextState)
                        updatedCount += 1
                    }
                }

                is TrackedAppDownloadStatus.Successful -> {
                    val localFilePath = status.localFilePath
                        ?.trim()
                        ?.takeIf(String::isNotBlank)
                        ?: download.localFilePath?.trim()?.takeIf(String::isNotBlank)
                    if (!localFilePath.isNullOrBlank()) {
                        completeDownload(
                            appId = download.appId,
                            localFilePath = localFilePath,
                        )
                        updatedCount += 1
                    } else {
                        val nextState = download.copy(
                            status = "failed",
                            downloadedBytes = null,
                            totalBytes = null,
                            downloadDetailMessage = null,
                            errorMessage = "Downloaded file is unavailable",
                            updatedAtEpochMs = nowEpochMs(),
                        )
                        if (nextState != download) {
                            downloadStore.upsert(nextState)
                            updatedCount += 1
                        }
                    }
                }

                is TrackedAppDownloadStatus.Failed -> {
                    val nextState = download.copy(
                        status = "failed",
                        downloadedBytes = null,
                        totalBytes = null,
                        downloadDetailMessage = null,
                        errorMessage = status.message,
                        updatedAtEpochMs = nowEpochMs(),
                    )
                    if (nextState != download) {
                        downloadStore.upsert(nextState)
                        updatedCount += 1
                    }
                }

                TrackedAppDownloadStatus.Missing -> {
                    val nextState = download.copy(
                        status = "failed",
                        downloadedBytes = null,
                        totalBytes = null,
                        downloadDetailMessage = null,
                        errorMessage = "DownloadManager record not found",
                        updatedAtEpochMs = nowEpochMs(),
                    )
                    if (nextState != download) {
                        downloadStore.upsert(nextState)
                        updatedCount += 1
                    }
                }
            }
        }
        return updatedCount
    }

    suspend fun retry(appId: String): StoredAppDownloadState? {
        val normalizedAppId = appId.trim()
        if (normalizedAppId.isBlank()) {
            return null
        }
        val currentState = downloadStore.read(normalizedAppId) ?: return null
        if (currentState.status.trim().lowercase() !in RetryableStatuses) {
            return currentState
        }
        return try {
            val queuedDownload = enqueuer.enqueue(
                AppDownloadRequest(
                    appId = currentState.appId,
                    title = currentState.title,
                    packageName = currentState.packageName,
                    versionCode = currentState.versionCode,
                    versionName = currentState.versionName,
                    downloadUrl = currentState.downloadUrl,
                    sha256 = currentState.sha256,
                ),
            )
            val queuedState = currentState.copy(
                status = "queued",
                downloadId = queuedDownload.downloadId,
                localFilePath = queuedDownload.localFilePath,
                downloadedBytes = null,
                totalBytes = null,
                downloadDetailMessage = null,
                errorMessage = null,
                updatedAtEpochMs = nowEpochMs(),
            )
            downloadStore.upsert(queuedState)
            queuedState
        } catch (error: Exception) {
            error.rethrowIfCancellation()
            val failedState = currentState.copy(
                status = "failed",
                downloadedBytes = null,
                totalBytes = null,
                downloadDetailMessage = null,
                errorMessage = error.message ?: "Failed to enqueue download",
                updatedAtEpochMs = nowEpochMs(),
            )
            downloadStore.upsert(failedState)
            failedState
        }
    }

    suspend fun installableDownloads(): List<StoredAppDownloadState> {
        return downloadStore.readAll()
            .values
            .filter { it.status == "ready_to_install" }
            .sortedBy { it.appId }
    }

    private suspend fun shouldSkipExistingDownload(app: StoredRuntimeApp): Boolean {
        val currentState = downloadStore.read(app.appId) ?: return false
        if (currentState.versionCode != app.versionCode || !currentState.sha256.equals(app.sha256, ignoreCase = true)) {
            return false
        }
        return currentState.status in NonTerminalStatuses
    }

    private fun shouldAutoDownload(app: StoredRuntimeApp): Boolean {
        if (app.appId.isBlank() || app.downloadUrl.isBlank() || app.sha256.isBlank()) {
            return false
        }
        return when (app.preloadPolicy.trim().lowercase()) {
            "auto",
            "background",
            "idle_only",
            -> true

            else -> false
        }
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        val normalizedPackageName = packageName.trim()
        if (normalizedPackageName.isBlank()) {
            return false
        }
        return installedPackageChecker?.isInstalled(normalizedPackageName) == true
    }

    private companion object {
        val NonTerminalStatuses = setOf(
            "queued",
            "downloading",
            "downloaded",
            "verifying",
            "ready_to_install",
        )
        val RetryableStatuses = setOf(
            "failed",
        )
        val TrackableStatuses = setOf(
            "queued",
            "downloading",
            "paused",
            "downloaded",
            "verifying",
        )
    }
}

private fun Throwable.rethrowIfCancellation() {
    if (this is CancellationException) {
        throw this
    }
}
