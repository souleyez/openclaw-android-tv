package com.openclaw.tv.feature.appdelivery

import com.openclaw.tv.core.storage.AppDownloadStore
import com.openclaw.tv.core.storage.StoredAppDownloadState
import com.openclaw.tv.core.storage.StoredRuntimeApp
import com.openclaw.tv.core.storage.StoredRuntimeManifest

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
            errorMessage = null,
            updatedAtEpochMs = nowEpochMs(),
        )
        downloadStore.upsert(downloadedState)

        val verifyingState = downloadedState.copy(
            status = "verifying",
            updatedAtEpochMs = nowEpochMs(),
        )
        downloadStore.upsert(verifyingState)

        return try {
            val actualSha256 = checksumVerifier.sha256(localFilePath)
            if (!actualSha256.equals(verifyingState.sha256, ignoreCase = true)) {
                val failedState = verifyingState.copy(
                    status = "failed",
                    errorMessage = "SHA-256 mismatch",
                    updatedAtEpochMs = nowEpochMs(),
                )
                downloadStore.upsert(failedState)
                failedState
            } else {
                val readyState = verifyingState.copy(
                    status = "ready_to_install",
                    errorMessage = null,
                    updatedAtEpochMs = nowEpochMs(),
                )
                downloadStore.upsert(readyState)
                readyState
            }
        } catch (error: Exception) {
            val failedState = verifyingState.copy(
                status = "failed",
                errorMessage = error.message ?: "Checksum verification failed",
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

    private companion object {
        val NonTerminalStatuses = setOf(
            "queued",
            "downloading",
            "downloaded",
            "verifying",
            "ready_to_install",
        )
    }
}
