package com.openclaw.tv.runtime

import com.openclaw.tv.core.storage.AppDownloadStore
import com.openclaw.tv.core.storage.StoredAppDownloadState
import com.openclaw.tv.feature.appdelivery.AppDownloadCoordinator
import com.openclaw.tv.feature.appdelivery.TrackedAppDownloadStatus
import com.openclaw.tv.feature.appdelivery.TrackedAppDownloadStatusResolver

data class AppDownloadStartupRecoverySummary(
    val resumedCount: Int = 0,
    val completedCount: Int = 0,
    val failedCount: Int = 0,
)

class AppDownloadStartupReconciler(
    private val downloadStore: AppDownloadStore,
    private val coordinator: AppDownloadCoordinator,
    private val statusResolver: TrackedAppDownloadStatusResolver,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {

    suspend fun reconcile(): AppDownloadStartupRecoverySummary {
        var resumedCount = 0
        var completedCount = 0
        var failedCount = 0
        val trackedDownloads = downloadStore.readAll()
            .values
            .filter { it.status in RecoverableStatuses }
        for (download in trackedDownloads) {
            val resolvedStatus = resolveTrackedStatus(download)
            when (resolvedStatus) {
                is TrackedAppDownloadStatus.Pending -> {
                    if (download.status != "queued" ||
                        download.downloadedBytes != resolvedStatus.downloadedBytes ||
                        download.totalBytes != resolvedStatus.totalBytes ||
                        !download.downloadDetailMessage.isNullOrBlank()
                    ) {
                        downloadStore.upsert(
                            download.copy(
                                status = "queued",
                                downloadedBytes = resolvedStatus.downloadedBytes,
                                totalBytes = resolvedStatus.totalBytes,
                                downloadDetailMessage = null,
                                errorMessage = null,
                                updatedAtEpochMs = nowEpochMs(),
                            ),
                        )
                        resumedCount += 1
                    }
                }

                is TrackedAppDownloadStatus.Running -> {
                    if (download.status != "downloading" ||
                        download.downloadedBytes != resolvedStatus.downloadedBytes ||
                        download.totalBytes != resolvedStatus.totalBytes ||
                        !download.downloadDetailMessage.isNullOrBlank()
                    ) {
                        downloadStore.upsert(
                            download.copy(
                                status = "downloading",
                                downloadedBytes = resolvedStatus.downloadedBytes,
                                totalBytes = resolvedStatus.totalBytes,
                                downloadDetailMessage = null,
                                errorMessage = null,
                                updatedAtEpochMs = nowEpochMs(),
                            ),
                        )
                        resumedCount += 1
                    }
                }

                is TrackedAppDownloadStatus.Paused -> {
                    downloadStore.upsert(
                        download.copy(
                            status = "paused",
                            downloadedBytes = resolvedStatus.downloadedBytes,
                            totalBytes = resolvedStatus.totalBytes,
                            downloadDetailMessage = resolvedStatus.message,
                            errorMessage = null,
                            updatedAtEpochMs = nowEpochMs(),
                        ),
                    )
                    resumedCount += 1
                }

                is TrackedAppDownloadStatus.Successful -> {
                    val localFilePath = resolvedStatus.localFilePath
                        ?.trim()
                        ?.takeIf(String::isNotBlank)
                        ?: download.localFilePath?.trim()?.takeIf(String::isNotBlank)
                    if (localFilePath.isNullOrBlank()) {
                        downloadStore.upsert(
                            download.copy(
                                status = "failed",
                                downloadedBytes = null,
                                totalBytes = null,
                                downloadDetailMessage = null,
                                errorMessage = "Downloaded file is unavailable",
                                updatedAtEpochMs = nowEpochMs(),
                            ),
                        )
                        failedCount += 1
                    } else {
                        coordinator.completeDownload(
                            appId = download.appId,
                            localFilePath = localFilePath,
                        )
                        completedCount += 1
                    }
                }

                is TrackedAppDownloadStatus.Failed -> {
                    downloadStore.upsert(
                        download.copy(
                            status = "failed",
                            downloadedBytes = null,
                            totalBytes = null,
                            downloadDetailMessage = null,
                            errorMessage = resolvedStatus.message,
                            updatedAtEpochMs = nowEpochMs(),
                        ),
                    )
                    failedCount += 1
                }

                TrackedAppDownloadStatus.Missing -> {
                    downloadStore.upsert(
                        download.copy(
                            status = "failed",
                            downloadedBytes = null,
                            totalBytes = null,
                            downloadDetailMessage = null,
                            errorMessage = "DownloadManager record not found",
                            updatedAtEpochMs = nowEpochMs(),
                        ),
                    )
                    failedCount += 1
                }
            }
        }
        return AppDownloadStartupRecoverySummary(
            resumedCount = resumedCount,
            completedCount = completedCount,
            failedCount = failedCount,
        )
    }

    private fun resolveTrackedStatus(download: StoredAppDownloadState): TrackedAppDownloadStatus {
        val downloadId = download.downloadId?.takeIf { it > 0L } ?: return TrackedAppDownloadStatus.Missing
        return statusResolver.resolve(downloadId)
    }

    private companion object {
        val RecoverableStatuses = setOf(
            "queued",
            "downloading",
            "paused",
            "downloaded",
            "verifying",
            "ready_to_install",
        )
    }
}
