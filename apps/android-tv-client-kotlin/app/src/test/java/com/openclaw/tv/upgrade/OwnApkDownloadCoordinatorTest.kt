package com.openclaw.tv.upgrade

import com.openclaw.tv.core.network.dto.OwnApkUpdateManifestDto
import com.openclaw.tv.core.network.dto.OwnApkUpdateReportEnvelope
import com.openclaw.tv.core.network.dto.OwnApkUpdateReportRequestDto
import com.openclaw.tv.feature.appdelivery.AppChecksumVerifier
import com.openclaw.tv.feature.appdelivery.TrackedAppDownloadStatus
import com.openclaw.tv.feature.appdelivery.TrackedAppDownloadStatusResolver
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnApkDownloadCoordinatorTest {

    @Test
    fun reconcile_verifies_successful_download_and_reports_downloaded_then_verified() = runTest {
        val store = InMemoryOwnApkDownloadStore(update(status = "downloading"))
        val repository = FakeRepository()
        val coordinator = OwnApkDownloadCoordinator(
            store = store,
            repository = repository,
            statusResolver = FixedStatusResolver(TrackedAppDownloadStatus.Successful("/downloads/openclaw.apk")),
            checksumVerifier = FixedChecksumVerifier("sha-full"),
            nowEpochMs = { 200L },
        )

        val result = coordinator.reconcile(
            sessionToken = "session_token",
            currentVersionCode = 10,
        )

        assertTrue(result)
        assertEquals("verified", store.read()?.status)
        assertEquals("/downloads/openclaw.apk", store.read()?.localPath)
        assertNull(store.read()?.errorMessage)
        assertEquals(listOf("downloaded", "verified"), repository.reports.map { it.status })
    }

    @Test
    fun reconcile_marks_failed_when_checksum_does_not_match() = runTest {
        val store = InMemoryOwnApkDownloadStore(update(status = "downloading"))
        val repository = FakeRepository()
        val coordinator = OwnApkDownloadCoordinator(
            store = store,
            repository = repository,
            statusResolver = FixedStatusResolver(TrackedAppDownloadStatus.Successful("/downloads/openclaw.apk")),
            checksumVerifier = FixedChecksumVerifier("wrong-sha"),
            nowEpochMs = { 300L },
        )

        val result = coordinator.reconcile(
            sessionToken = "session_token",
            currentVersionCode = 10,
        )

        assertEquals(false, result)
        assertEquals("failed", store.read()?.status)
        assertEquals("SHA-256 mismatch", store.read()?.errorMessage)
        assertEquals(listOf("downloaded", "failed"), repository.reports.map { it.status })
    }

    @Test
    fun reconcile_reports_installed_and_clears_store_after_version_change() = runTest {
        val store = InMemoryOwnApkDownloadStore(update(status = "verified", targetVersionCode = 11))
        val repository = FakeRepository()
        val coordinator = OwnApkDownloadCoordinator(
            store = store,
            repository = repository,
            statusResolver = FixedStatusResolver(TrackedAppDownloadStatus.Missing),
            checksumVerifier = FixedChecksumVerifier("sha-full"),
        )

        val result = coordinator.reconcile(
            sessionToken = "session_token",
            currentVersionCode = 11,
        )

        assertTrue(result)
        assertNull(store.read())
        assertEquals(listOf("installed"), repository.reports.map { it.status })
    }

    private fun update(
        status: String,
        targetVersionCode: Long = 11,
    ) = StoredOwnApkUpdate(
        releaseId = "full_1",
        updateMode = "full_apk",
        targetVersionCode = targetVersionCode,
        targetVersionName = "0.1.5",
        downloadUrl = "https://cdn.example.com/openclaw.apk",
        sha256 = "sha-full",
        size = 4096L,
        installPolicy = "deferred_prompt",
        downloadId = 42L,
        localPath = "",
        status = status,
        updatedAtEpochMs = 100L,
    )

    private class FixedStatusResolver(
        private val status: TrackedAppDownloadStatus,
    ) : TrackedAppDownloadStatusResolver {
        override fun resolve(downloadId: Long): TrackedAppDownloadStatus = status
    }

    private class FixedChecksumVerifier(
        private val sha256: String,
    ) : AppChecksumVerifier {
        override suspend fun sha256(localFilePath: String): String = sha256
    }

    private class FakeRepository : OwnApkUpdateRepository {
        val reports = mutableListOf<OwnApkUpdateReportRequestDto>()

        override suspend fun fetchManifest(
            sessionToken: String,
            currentVersionCode: Long,
            currentResourceVersion: String?,
        ): OwnApkUpdateManifestDto = OwnApkUpdateManifestDto()

        override suspend fun report(
            sessionToken: String,
            request: OwnApkUpdateReportRequestDto,
        ): OwnApkUpdateReportEnvelope {
            reports += request
            return OwnApkUpdateReportEnvelope(status = "ok")
        }
    }
}
