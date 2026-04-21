package com.openclaw.tv.core.storage

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppDownloadStoreTest {

    @Test
    fun upsert_then_read_round_trips_download_state() = runTest {
        val store = InMemoryAppDownloadStore()
        val expected = StoredAppDownloadState(
            appId = "youtube",
            title = "YouTube",
            packageName = "com.google.android.youtube.tv",
            versionCode = 1001L,
            versionName = "1.0.1",
            downloadUrl = "https://cdn.example.com/youtube.apk",
            sha256 = "sha256-1",
            status = "ready_to_install",
            downloadId = 42L,
            localFilePath = "/downloads/youtube.apk",
            updatedAtEpochMs = 100L,
        )

        store.upsert(expected)

        assertEquals(expected, store.read("youtube"))
        assertEquals(mapOf("youtube" to expected), store.readAll())
    }

    @Test
    fun remove_deletes_single_app_without_touching_others() = runTest {
        val store = InMemoryAppDownloadStore(
            mapOf(
                "youtube" to StoredAppDownloadState(
                    appId = "youtube",
                    title = "YouTube",
                    packageName = "com.google.android.youtube.tv",
                    versionCode = 1001L,
                    versionName = "1.0.1",
                    downloadUrl = "https://cdn.example.com/youtube.apk",
                    sha256 = "sha256-1",
                    status = "queued",
                    updatedAtEpochMs = 100L,
                ),
                "netflix" to StoredAppDownloadState(
                    appId = "netflix",
                    title = "Netflix",
                    packageName = "com.netflix.ninja",
                    versionCode = 2002L,
                    versionName = "2.0.2",
                    downloadUrl = "https://cdn.example.com/netflix.apk",
                    sha256 = "sha256-2",
                    status = "failed",
                    errorMessage = "SHA-256 mismatch",
                    updatedAtEpochMs = 200L,
                ),
            ),
        )

        store.remove("youtube")

        assertNull(store.read("youtube"))
        assertEquals(setOf("netflix"), store.readAll().keys)
    }

    @Test
    fun clear_removes_all_downloads() = runTest {
        val store = InMemoryAppDownloadStore(
            mapOf(
                "youtube" to StoredAppDownloadState(
                    appId = "youtube",
                    title = "YouTube",
                    packageName = "com.google.android.youtube.tv",
                    versionCode = 1001L,
                    versionName = "1.0.1",
                    downloadUrl = "https://cdn.example.com/youtube.apk",
                    sha256 = "sha256-1",
                    status = "downloading",
                    downloadId = 42L,
                    localFilePath = "/downloads/youtube.apk",
                    updatedAtEpochMs = 100L,
                ),
            ),
        )

        store.clear()

        assertEquals(emptyMap<String, StoredAppDownloadState>(), store.readAll())
    }
}
