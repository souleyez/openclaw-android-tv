package com.openclaw.tv.feature.appdelivery

import com.openclaw.tv.core.storage.InMemoryAppDownloadStore
import com.openclaw.tv.core.storage.StoredAppDownloadState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppInstallStateTrackerTest {

    @Test
    fun reconcile_clears_download_records_for_already_installed_packages() = runTest {
        val store = InMemoryAppDownloadStore(
            initial = mapOf(
                "youtube" to downloadState(
                    appId = "youtube",
                    packageName = "com.google.android.youtube.tv",
                ),
                "netflix" to downloadState(
                    appId = "netflix",
                    packageName = "com.netflix.ninja",
                ),
            ),
        )
        val tracker = AppInstallStateTracker(
            downloadStore = store,
            installedPackageChecker = InstalledPackageChecker { packageName ->
                packageName == "com.google.android.youtube.tv"
            },
        )

        val removedAppIds = tracker.reconcileInstalledPackages()

        assertEquals(listOf("youtube"), removedAppIds)
        assertNull(store.read("youtube"))
        assertTrue(store.read("netflix") != null)
    }

    @Test
    fun package_added_clears_matching_download_records() = runTest {
        val store = InMemoryAppDownloadStore(
            initial = mapOf(
                "youtube" to downloadState(
                    appId = "youtube",
                    packageName = "com.google.android.youtube.tv",
                ),
                "spotify" to downloadState(
                    appId = "spotify",
                    packageName = "com.spotify.tv.android",
                ),
            ),
        )
        val tracker = AppInstallStateTracker(
            downloadStore = store,
            installedPackageChecker = InstalledPackageChecker { false },
        )

        val removedAppIds = tracker.handlePackageInstalled("com.spotify.tv.android")

        assertEquals(listOf("spotify"), removedAppIds)
        assertTrue(store.read("youtube") != null)
        assertNull(store.read("spotify"))
    }

    private fun downloadState(
        appId: String,
        packageName: String,
    ): StoredAppDownloadState {
        return StoredAppDownloadState(
            appId = appId,
            title = appId,
            packageName = packageName,
            versionCode = 1001L,
            versionName = "1.0.1",
            downloadUrl = "https://cdn.example.com/$appId.apk",
            sha256 = "sha256-$appId",
            status = "ready_to_install",
            downloadId = 1L,
            localFilePath = "/downloads/$appId.apk",
            errorMessage = null,
            updatedAtEpochMs = 100L,
        )
    }
}
