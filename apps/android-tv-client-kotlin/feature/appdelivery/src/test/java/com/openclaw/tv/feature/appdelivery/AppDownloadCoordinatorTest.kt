package com.openclaw.tv.feature.appdelivery

import com.openclaw.tv.core.storage.InMemoryAppDownloadStore
import com.openclaw.tv.core.storage.StoredRuntimeApp
import com.openclaw.tv.core.storage.StoredRuntimeManifest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDownloadCoordinatorTest {

    @Test
    fun idle_only_downloads_do_not_enqueue_while_device_is_active() = runTest {
        val enqueuer = FakeAppDownloadEnqueuer()
        val coordinator = AppDownloadCoordinator(
            downloadStore = InMemoryAppDownloadStore(),
            enqueuer = enqueuer,
            checksumVerifier = FakeChecksumVerifier("sha256-youtube"),
            nowEpochMs = { 100L },
        )

        val queued = coordinator.enqueueEligibleDownloads(
            manifest = manifestWith(preloadPolicy = "idle_only"),
            backgroundDownloadEnabled = true,
            idleDownloadOnly = false,
            deviceIsActive = true,
        )

        assertTrue(queued.isEmpty())
        assertEquals(0, enqueuer.requests.size)
    }

    @Test
    fun checksum_mismatch_marks_download_failed_and_not_installable() = runTest {
        val downloadStore = InMemoryAppDownloadStore()
        val coordinator = AppDownloadCoordinator(
            downloadStore = downloadStore,
            enqueuer = FakeAppDownloadEnqueuer(),
            checksumVerifier = FakeChecksumVerifier("wrong-sha"),
            nowEpochMs = { 200L },
        )

        coordinator.enqueueEligibleDownloads(
            manifest = manifestWith(preloadPolicy = "auto"),
            backgroundDownloadEnabled = true,
            idleDownloadOnly = false,
            deviceIsActive = false,
        )
        val completed = coordinator.completeDownload(
            appId = "youtube",
            localFilePath = "/downloads/youtube.apk",
        )
        val installable = coordinator.installableDownloads()

        assertEquals("failed", completed?.status)
        assertEquals("SHA-256 mismatch", completed?.errorMessage)
        assertTrue(installable.isEmpty())
        assertNull(installable.firstOrNull())
        assertEquals("failed", downloadStore.read("youtube")?.status)
    }

    @Test
    fun failed_download_can_be_retried_and_requeued() = runTest {
        val downloadStore = InMemoryAppDownloadStore(
            mapOf(
                "youtube" to failedDownloadState(),
            ),
        )
        val enqueuer = FakeAppDownloadEnqueuer()
        val coordinator = AppDownloadCoordinator(
            downloadStore = downloadStore,
            enqueuer = enqueuer,
            checksumVerifier = FakeChecksumVerifier("sha256-youtube"),
            nowEpochMs = { 300L },
        )

        val retried = coordinator.retry("youtube")

        assertEquals(1, enqueuer.requests.size)
        assertEquals("queued", retried?.status)
        assertEquals(1L, retried?.downloadId)
        assertNull(retried?.errorMessage)
        assertEquals("queued", downloadStore.read("youtube")?.status)
    }

    @Test
    fun retry_failure_keeps_download_failed_with_new_reason() = runTest {
        val downloadStore = InMemoryAppDownloadStore(
            mapOf(
                "youtube" to failedDownloadState(),
            ),
        )
        val coordinator = AppDownloadCoordinator(
            downloadStore = downloadStore,
            enqueuer = FakeAppDownloadEnqueuer(
                error = IllegalStateException("network down"),
            ),
            checksumVerifier = FakeChecksumVerifier("sha256-youtube"),
            nowEpochMs = { 400L },
        )

        val retried = coordinator.retry("youtube")

        assertEquals("failed", retried?.status)
        assertEquals("network down", retried?.errorMessage)
        assertEquals("failed", downloadStore.read("youtube")?.status)
        assertEquals("network down", downloadStore.read("youtube")?.errorMessage)
    }

    @Test
    fun tracked_running_download_updates_progress() = runTest {
        val downloadStore = InMemoryAppDownloadStore(
            mapOf(
                "youtube" to failedDownloadState().copy(
                    status = "queued",
                    errorMessage = null,
                    downloadedBytes = null,
                    totalBytes = null,
                    downloadDetailMessage = null,
                ),
            ),
        )
        val coordinator = AppDownloadCoordinator(
            downloadStore = downloadStore,
            enqueuer = FakeAppDownloadEnqueuer(),
            checksumVerifier = FakeChecksumVerifier("sha256-youtube"),
            nowEpochMs = { 500L },
        )

        val updatedCount = coordinator.refreshTrackedDownloads(
            statusResolver = FakeTrackedAppDownloadStatusResolver(
                mapOf(
                    7L to TrackedAppDownloadStatus.Running(
                        downloadedBytes = 512L,
                        totalBytes = 1_024L,
                    ),
                ),
            ),
        )

        assertEquals(1, updatedCount)
        assertEquals("downloading", downloadStore.read("youtube")?.status)
        assertEquals(512L, downloadStore.read("youtube")?.downloadedBytes)
        assertEquals(1_024L, downloadStore.read("youtube")?.totalBytes)
    }

    @Test
    fun tracked_paused_download_updates_detail_message() = runTest {
        val downloadStore = InMemoryAppDownloadStore(
            mapOf(
                "youtube" to failedDownloadState().copy(
                    status = "downloading",
                    errorMessage = null,
                ),
            ),
        )
        val coordinator = AppDownloadCoordinator(
            downloadStore = downloadStore,
            enqueuer = FakeAppDownloadEnqueuer(),
            checksumVerifier = FakeChecksumVerifier("sha256-youtube"),
            nowEpochMs = { 600L },
        )

        val updatedCount = coordinator.refreshTrackedDownloads(
            statusResolver = FakeTrackedAppDownloadStatusResolver(
                mapOf(
                    7L to TrackedAppDownloadStatus.Paused(
                        message = "等待 Wi-Fi 后继续下载",
                        downloadedBytes = 256L,
                        totalBytes = 1_024L,
                    ),
                ),
            ),
        )

        assertEquals(1, updatedCount)
        assertEquals("paused", downloadStore.read("youtube")?.status)
        assertEquals("等待 Wi-Fi 后继续下载", downloadStore.read("youtube")?.downloadDetailMessage)
    }

    private fun manifestWith(preloadPolicy: String): StoredRuntimeManifest {
        return StoredRuntimeManifest(
            manifestVersion = "remote-v1",
            countryCode = "CN",
            regionCode = "SH",
            apps = listOf(
                StoredRuntimeApp(
                    appId = "youtube",
                    title = "YouTube",
                    packageName = "com.google.android.youtube.tv",
                    downloadUrl = "https://cdn.example.com/youtube.apk",
                    sha256 = "sha256-youtube",
                    versionCode = 1001L,
                    versionName = "1.0.1",
                    minClientVersion = "0.1.0",
                    installMode = "prompt",
                    visibility = "featured",
                    preloadPolicy = preloadPolicy,
                    requiresEntitlement = false,
                ),
            ),
            adSlots = emptyList(),
            cachedAtEpochMs = 100L,
        )
    }

    private fun failedDownloadState(): com.openclaw.tv.core.storage.StoredAppDownloadState {
        return com.openclaw.tv.core.storage.StoredAppDownloadState(
            appId = "youtube",
            title = "YouTube",
            packageName = "com.google.android.youtube.tv",
            versionCode = 1001L,
            versionName = "1.0.1",
            downloadUrl = "https://cdn.example.com/youtube.apk",
            sha256 = "sha256-youtube",
            status = "failed",
            downloadId = 7L,
            localFilePath = "/downloads/youtube.apk",
            errorMessage = "DownloadManager record not found",
            updatedAtEpochMs = 100L,
        )
    }

    private class FakeAppDownloadEnqueuer(
        private val error: Throwable? = null,
    ) : AppDownloadEnqueuer {
        val requests = mutableListOf<AppDownloadRequest>()

        override suspend fun enqueue(request: AppDownloadRequest): QueuedAppDownload {
            error?.let { throw it }
            requests += request
            return QueuedAppDownload(
                downloadId = requests.size.toLong(),
            )
        }
    }

    private class FakeChecksumVerifier(
        private val digest: String,
    ) : AppChecksumVerifier {
        override suspend fun sha256(localFilePath: String): String = digest
    }

    private class FakeTrackedAppDownloadStatusResolver(
        private val statuses: Map<Long, TrackedAppDownloadStatus>,
    ) : TrackedAppDownloadStatusResolver {
        override fun resolve(downloadId: Long): TrackedAppDownloadStatus {
            return statuses[downloadId] ?: TrackedAppDownloadStatus.Missing
        }
    }
}
