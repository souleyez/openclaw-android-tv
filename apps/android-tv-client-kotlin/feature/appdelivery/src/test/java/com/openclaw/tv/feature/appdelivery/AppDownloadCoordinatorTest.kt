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

    private class FakeAppDownloadEnqueuer : AppDownloadEnqueuer {
        val requests = mutableListOf<AppDownloadRequest>()

        override suspend fun enqueue(request: AppDownloadRequest): QueuedAppDownload {
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
}
