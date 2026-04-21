package com.openclaw.tv.feature.appdelivery

import android.net.Uri
import android.os.Build
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ApkInstallUriResolverTest {

    @Test
    fun resolver_prefers_download_manager_uri_when_available() {
        val downloadedFileUri = Uri.parse("content://downloads/all_downloads/42")
        val resolved = resolveDownloadedOrLocalApkUri(
            downloadedFileUri = downloadedFileUri,
            localFilePath = null,
            localFileUriProvider = { error("Local file fallback should not run") },
        )

        assertEquals(downloadedFileUri, resolved)
    }

    @Test
    fun resolver_uses_file_provider_fallback_on_android_n_and_above() {
        val apkFile = Files.createTempFile("openclaw-apk-", ".apk").toFile().apply {
            writeText("apk")
        }
        val contentUri = Uri.parse("content://com.openclaw.tv.fileprovider/downloads/${apkFile.name}")

        val resolved = resolveDownloadedOrLocalApkUri(
            downloadedFileUri = null,
            localFilePath = apkFile.absolutePath,
            sdkInt = Build.VERSION_CODES.N,
            localFileUriProvider = { file ->
                assertEquals(apkFile.absolutePath, file.absolutePath)
                contentUri
            },
        )

        assertEquals(contentUri, resolved)
        apkFile.delete()
    }

    @Test
    fun resolver_uses_file_uri_before_android_n() {
        val apkFile = Files.createTempFile("openclaw-apk-", ".apk").toFile().apply {
            writeText("apk")
        }

        val resolved = resolveDownloadedOrLocalApkUri(
            downloadedFileUri = null,
            localFilePath = apkFile.absolutePath,
            sdkInt = Build.VERSION_CODES.M,
            localFileUriProvider = { error("FileProvider fallback should not run before Android N") },
        )

        assertEquals(Uri.fromFile(apkFile), resolved)
        apkFile.delete()
    }

    @Test
    fun resolver_returns_null_when_local_apk_file_is_missing() {
        val missingFile = File(System.getProperty("java.io.tmpdir"), "openclaw-missing-${System.nanoTime()}.apk")

        val resolved = resolveDownloadedOrLocalApkUri(
            downloadedFileUri = null,
            localFilePath = missingFile.absolutePath,
            sdkInt = Build.VERSION_CODES.N,
            localFileUriProvider = { error("Local file provider should not run for missing files") },
        )

        assertNull(resolved)
        assertTrue(!missingFile.exists())
    }
}
