package com.openclaw.tv.runtime

import com.openclaw.tv.core.storage.InMemoryAppDownloadStore
import com.openclaw.tv.core.storage.StoredAppDownloadState
import com.openclaw.tv.feature.appdelivery.AppChecksumVerifier
import com.openclaw.tv.feature.appdelivery.AppDownloadCoordinator
import com.openclaw.tv.feature.appdelivery.AppDownloadEnqueuer
import com.openclaw.tv.feature.appdelivery.AppDownloadRequest
import com.openclaw.tv.feature.appdelivery.QueuedAppDownload
import com.openclaw.tv.feature.appdelivery.TrackedAppDownloadStatus
import com.openclaw.tv.feature.appdelivery.TrackedAppDownloadStatusResolver
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AppDownloadStartupReconcilerTest {

    @Test
    fun successful_download_is_completed_on_startup_reconcile() = runTest {
        val store = InMemoryAppDownloadStore(
            mapOf(
                "youtube" to downloadState(
                    status = "downloading",
                    downloadId = 42L,
                    localFilePath = "/downloads/youtube.apk",
                ),
            ),
        )
        val coordinator = AppDownloadCoordinator(
            downloadStore = store,
            enqueuer = NoopAppDownloadEnqueuer(),
            checksumVerifier = FixedChecksumVerifier("sha256-youtube"),
            nowEpochMs = { 200L },
        )
        val reconciler = AppDownloadStartupReconciler(
            downloadStore = store,
            coordinator = coordinator,
            statusResolver = FakeTrackedAppDownloadStatusResolver(
                mapOf(42L to TrackedAppDownloadStatus.Successful("/downloads/youtube.apk")),
            ),
            nowEpochMs = { 150L },
        )

        val summary = reconciler.reconcile()

        assertEquals(AppDownloadStartupRecoverySummary(completedCount = 1), summary)
        assertEquals("ready_to_install", store.read("youtube")?.status)
        assertEquals(null, store.read("youtube")?.errorMessage)
    }

    @Test
    fun failed_download_is_marked_failed_on_startup_reconcile() = runTest {
        val store = InMemoryAppDownloadStore(
            mapOf(
                "youtube" to downloadState(
                    status = "downloading",
                    downloadId = 42L,
                ),
            ),
        )
        val coordinator = AppDownloadCoordinator(
            downloadStore = store,
            enqueuer = NoopAppDownloadEnqueuer(),
            checksumVerifier = FixedChecksumVerifier("sha256-youtube"),
            nowEpochMs = { 200L },
        )
        val reconciler = AppDownloadStartupReconciler(
            downloadStore = store,
            coordinator = coordinator,
            statusResolver = FakeTrackedAppDownloadStatusResolver(
                mapOf(42L to TrackedAppDownloadStatus.Failed("Network error")),
            ),
            nowEpochMs = { 150L },
        )

        val summary = reconciler.reconcile()

        assertEquals(AppDownloadStartupRecoverySummary(failedCount = 1), summary)
        assertEquals("failed", store.read("youtube")?.status)
        assertEquals("Network error", store.read("youtube")?.errorMessage)
    }

    @Test
    fun in_progress_download_is_normalized_to_downloading_on_startup_reconcile() = runTest {
        val store = InMemoryAppDownloadStore(
            mapOf(
                "youtube" to downloadState(
                    status = "queued",
                    downloadId = 42L,
                ),
            ),
        )
        val coordinator = AppDownloadCoordinator(
            downloadStore = store,
            enqueuer = NoopAppDownloadEnqueuer(),
            checksumVerifier = FixedChecksumVerifier("sha256-youtube"),
            nowEpochMs = { 200L },
        )
        val reconciler = AppDownloadStartupReconciler(
            downloadStore = store,
            coordinator = coordinator,
            statusResolver = FakeTrackedAppDownloadStatusResolver(
                mapOf(
                    42L to TrackedAppDownloadStatus.Running(
                        downloadedBytes = 512L,
                        totalBytes = 1_024L,
                    ),
                ),
            ),
            nowEpochMs = { 150L },
        )

        val summary = reconciler.reconcile()

        assertEquals(AppDownloadStartupRecoverySummary(resumedCount = 1), summary)
        assertEquals("downloading", store.read("youtube")?.status)
        assertEquals(512L, store.read("youtube")?.downloadedBytes)
        assertEquals(1_024L, store.read("youtube")?.totalBytes)
        assertEquals(null, store.read("youtube")?.errorMessage)
    }

    @Test
    fun missing_ready_to_install_download_is_marked_failed_on_startup_reconcile() = runTest {
        val store = InMemoryAppDownloadStore(
            mapOf(
                "youtube" to downloadState(
                    status = "ready_to_install",
                    downloadId = 42L,
                ),
            ),
        )
        val coordinator = AppDownloadCoordinator(
            downloadStore = store,
            enqueuer = NoopAppDownloadEnqueuer(),
            checksumVerifier = FixedChecksumVerifier("sha256-youtube"),
            nowEpochMs = { 200L },
        )
        val reconciler = AppDownloadStartupReconciler(
            downloadStore = store,
            coordinator = coordinator,
            statusResolver = FakeTrackedAppDownloadStatusResolver(
                mapOf(42L to TrackedAppDownloadStatus.Missing),
            ),
            nowEpochMs = { 150L },
        )

        val summary = reconciler.reconcile()

        assertEquals(AppDownloadStartupRecoverySummary(failedCount = 1), summary)
        assertEquals("failed", store.read("youtube")?.status)
        assertEquals("DownloadManager record not found", store.read("youtube")?.errorMessage)
    }

    private fun downloadState(
        status: String,
        downloadId: Long?,
        localFilePath: String? = null,
    ): StoredAppDownloadState {
        return StoredAppDownloadState(
            appId = "youtube",
            title = "YouTube",
            packageName = "com.google.android.youtube.tv",
            versionCode = 1001L,
            versionName = "1.0.1",
            downloadUrl = "https://cdn.example.com/youtube.apk",
            sha256 = "sha256-youtube",
            status = status,
            downloadId = downloadId,
            localFilePath = localFilePath,
            errorMessage = null,
            updatedAtEpochMs = 100L,
        )
    }

    private class FakeTrackedAppDownloadStatusResolver(
        private val statuses: Map<Long, TrackedAppDownloadStatus>,
    ) : TrackedAppDownloadStatusResolver {
        override fun resolve(downloadId: Long): TrackedAppDownloadStatus {
            return statuses[downloadId] ?: TrackedAppDownloadStatus.Missing
        }
    }

    private class NoopAppDownloadEnqueuer : AppDownloadEnqueuer {
        override suspend fun enqueue(request: AppDownloadRequest): QueuedAppDownload {
            error("enqueue should not be called during startup reconcile")
        }
    }

    private class FixedChecksumVerifier(
        private val checksum: String,
    ) : AppChecksumVerifier {
        override suspend fun sha256(localFilePath: String): String = checksum
    }
}
