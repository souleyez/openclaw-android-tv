package com.openclaw.tv.feature.appdelivery

import com.openclaw.tv.core.storage.InMemoryAppDownloadStore
import com.openclaw.tv.core.storage.StoredRuntimeApp
import com.openclaw.tv.core.storage.StoredRuntimeManifest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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
    fun installed_package_is_not_reenqueued_for_background_download() = runTest {
        val enqueuer = FakeAppDownloadEnqueuer()
        val coordinator = AppDownloadCoordinator(
            downloadStore = InMemoryAppDownloadStore(),
            enqueuer = enqueuer,
            checksumVerifier = FakeChecksumVerifier("sha256-youtube"),
            installedPackageChecker = InstalledPackageChecker { packageName ->
                packageName == "com.google.android.youtube.tv"
            },
            nowEpochMs = { 150L },
        )

        val queued = coordinator.enqueueEligibleDownloads(
            manifest = manifestWith(preloadPolicy = "auto"),
            backgroundDownloadEnabled = true,
            idleDownloadOnly = false,
            deviceIsActive = false,
        )

        assertTrue(queued.isEmpty())
        assertEquals(0, enqueuer.requests.size)
    }

    @Test
    fun manual_download_enqueue_persists_prompt_app_queue_state() = runTest {
        val downloadStore = InMemoryAppDownloadStore()
        val enqueuer = FakeAppDownloadEnqueuer()
        val coordinator = AppDownloadCoordinator(
            downloadStore = downloadStore,
            enqueuer = enqueuer,
            checksumVerifier = FakeChecksumVerifier("sha256-youtube"),
            nowEpochMs = { 175L },
        )

        val queued = coordinator.enqueueManualDownload(
            AppDownloadRequest(
                appId = "youtube",
                title = "YouTube",
                packageName = "com.google.android.youtube.tv",
                versionCode = 1001L,
                versionName = "1.0.1",
                downloadUrl = "https://cdn.example.com/youtube.apk",
                sha256 = "sha256-youtube",
            ),
        )

        assertEquals(1, enqueuer.requests.size)
        assertEquals("queued", queued?.status)
        assertEquals(1L, queued?.downloadId)
        assertEquals("queued", downloadStore.read("youtube")?.status)
        assertEquals(175L, downloadStore.read("youtube")?.updatedAtEpochMs)
    }

    @Test
    fun manual_download_enqueue_failure_is_persisted_for_retry() = runTest {
        val downloadStore = InMemoryAppDownloadStore()
        val coordinator = AppDownloadCoordinator(
            downloadStore = downloadStore,
            enqueuer = FakeAppDownloadEnqueuer(error = IllegalStateException("network down")),
            checksumVerifier = FakeChecksumVerifier("sha256-youtube"),
            nowEpochMs = { 185L },
        )

        val failed = coordinator.enqueueManualDownload(
            AppDownloadRequest(
                appId = "youtube",
                title = "YouTube",
                packageName = "com.google.android.youtube.tv",
                versionCode = 1001L,
                versionName = "1.0.1",
                downloadUrl = "https://cdn.example.com/youtube.apk",
                sha256 = "sha256-youtube",
            ),
        )

        assertEquals("failed", failed?.status)
        assertEquals("network down", failed?.errorMessage)
        assertEquals("failed", downloadStore.read("youtube")?.status)
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
    fun checksum_cancellation_propagates_without_marking_download_failed() = runTest {
        val downloadStore = InMemoryAppDownloadStore()
        val coordinator = AppDownloadCoordinator(
            downloadStore = downloadStore,
            enqueuer = FakeAppDownloadEnqueuer(),
            checksumVerifier = FakeChecksumVerifier(
                error = CancellationException("verification cancelled"),
            ),
            nowEpochMs = { 250L },
        )

        coordinator.enqueueEligibleDownloads(
            manifest = manifestWith(preloadPolicy = "auto"),
            backgroundDownloadEnabled = true,
            idleDownloadOnly = false,
            deviceIsActive = false,
        )

        try {
            coordinator.completeDownload(
                appId = "youtube",
                localFilePath = "/downloads/youtube.apk",
            )
            fail("Expected checksum cancellation to propagate")
        } catch (expected: CancellationException) {
            assertEquals("verification cancelled", expected.message)
        }

        assertEquals("verifying", downloadStore.read("youtube")?.status)
        assertNull(downloadStore.read("youtube")?.errorMessage)
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
    fun retry_cancellation_propagates_without_overwriting_failed_state() = runTest {
        val downloadStore = InMemoryAppDownloadStore(
            mapOf(
                "youtube" to failedDownloadState(),
            ),
        )
        val coordinator = AppDownloadCoordinator(
            downloadStore = downloadStore,
            enqueuer = FakeAppDownloadEnqueuer(
                error = CancellationException("retry cancelled"),
            ),
            checksumVerifier = FakeChecksumVerifier("sha256-youtube"),
            nowEpochMs = { 450L },
        )

        try {
            coordinator.retry("youtube")
            fail("Expected retry cancellation to propagate")
        } catch (expected: CancellationException) {
            assertEquals("retry cancelled", expected.message)
        }

        assertEquals("failed", downloadStore.read("youtube")?.status)
        assertEquals("DownloadManager record not found", downloadStore.read("youtube")?.errorMessage)
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

    @Test
    fun tracked_successful_download_without_local_file_path_marks_failure() = runTest {
        val downloadStore = InMemoryAppDownloadStore(
            mapOf(
                "youtube" to failedDownloadState().copy(
                    status = "downloading",
                    localFilePath = null,
                    errorMessage = null,
                ),
            ),
        )
        val coordinator = AppDownloadCoordinator(
            downloadStore = downloadStore,
            enqueuer = FakeAppDownloadEnqueuer(),
            checksumVerifier = FakeChecksumVerifier("sha256-youtube"),
            nowEpochMs = { 700L },
        )

        val updatedCount = coordinator.refreshTrackedDownloads(
            statusResolver = FakeTrackedAppDownloadStatusResolver(
                mapOf(
                    7L to TrackedAppDownloadStatus.Successful(localFilePath = null),
                ),
            ),
        )

        assertEquals(1, updatedCount)
        assertEquals("failed", downloadStore.read("youtube")?.status)
        assertEquals("Downloaded file is unavailable", downloadStore.read("youtube")?.errorMessage)
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
        private val digest: String = "",
        private val error: Throwable? = null,
    ) : AppChecksumVerifier {
        override suspend fun sha256(localFilePath: String): String {
            error?.let { throw it }
            return digest
        }
    }

    private class FakeTrackedAppDownloadStatusResolver(
        private val statuses: Map<Long, TrackedAppDownloadStatus>,
    ) : TrackedAppDownloadStatusResolver {
        override fun resolve(downloadId: Long): TrackedAppDownloadStatus {
            return statuses[downloadId] ?: TrackedAppDownloadStatus.Missing
        }
    }
}
