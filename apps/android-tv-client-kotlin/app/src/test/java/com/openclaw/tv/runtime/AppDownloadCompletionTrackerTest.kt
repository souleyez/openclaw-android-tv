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
import org.junit.Assert.assertNull
import org.junit.Test

class AppDownloadCompletionTrackerTest {

    @Test
    fun successful_broadcast_completes_download_with_resolved_local_file_path() = runTest {
        val store = InMemoryAppDownloadStore(
            mapOf(
                "youtube" to downloadState(
                    status = "downloading",
                    localFilePath = null,
                ),
            ),
        )
        val tracker = AppDownloadCompletionTracker(
            downloadStore = store,
            coordinator = appDownloadCoordinator(store),
            statusResolver = FakeTrackedAppDownloadStatusResolver(
                mapOf(42L to TrackedAppDownloadStatus.Successful("/downloads/youtube.apk")),
            ),
            nowEpochMs = { 150L },
        )

        tracker.handleCompletedDownload(42L)

        assertEquals("ready_to_install", store.read("youtube")?.status)
        assertEquals("/downloads/youtube.apk", store.read("youtube")?.localFilePath)
        assertNull(store.read("youtube")?.errorMessage)
    }

    @Test
    fun successful_broadcast_without_any_local_file_path_marks_download_failed() = runTest {
        val store = InMemoryAppDownloadStore(
            mapOf(
                "youtube" to downloadState(
                    status = "downloading",
                    localFilePath = null,
                ),
            ),
        )
        val tracker = AppDownloadCompletionTracker(
            downloadStore = store,
            coordinator = appDownloadCoordinator(store),
            statusResolver = FakeTrackedAppDownloadStatusResolver(
                mapOf(42L to TrackedAppDownloadStatus.Successful(localFilePath = null)),
            ),
            nowEpochMs = { 175L },
        )

        tracker.handleCompletedDownload(42L)

        assertEquals("failed", store.read("youtube")?.status)
        assertEquals("Downloaded file is unavailable", store.read("youtube")?.errorMessage)
    }

    @Test
    fun failed_broadcast_marks_download_failed_with_download_manager_reason() = runTest {
        val store = InMemoryAppDownloadStore(
            mapOf(
                "youtube" to downloadState(
                    status = "downloading",
                ),
            ),
        )
        val tracker = AppDownloadCompletionTracker(
            downloadStore = store,
            coordinator = appDownloadCoordinator(store),
            statusResolver = FakeTrackedAppDownloadStatusResolver(
                mapOf(42L to TrackedAppDownloadStatus.Failed("Network error")),
            ),
            nowEpochMs = { 200L },
        )

        tracker.handleCompletedDownload(42L)

        assertEquals("failed", store.read("youtube")?.status)
        assertEquals("Network error", store.read("youtube")?.errorMessage)
    }

    private fun appDownloadCoordinator(
        store: InMemoryAppDownloadStore,
    ): AppDownloadCoordinator {
        return AppDownloadCoordinator(
            downloadStore = store,
            enqueuer = NoopAppDownloadEnqueuer(),
            checksumVerifier = FixedChecksumVerifier("sha256-youtube"),
            nowEpochMs = { 300L },
        )
    }

    private fun downloadState(
        status: String,
        localFilePath: String? = "/downloads/youtube.apk",
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
            downloadId = 42L,
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
            error("enqueue should not be called while handling completion broadcast")
        }
    }

    private class FixedChecksumVerifier(
        private val checksum: String,
    ) : AppChecksumVerifier {
        override suspend fun sha256(localFilePath: String): String = checksum
    }
}
