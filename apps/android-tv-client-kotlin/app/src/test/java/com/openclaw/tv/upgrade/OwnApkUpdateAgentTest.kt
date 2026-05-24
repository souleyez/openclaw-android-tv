package com.openclaw.tv.upgrade

import com.openclaw.tv.core.network.dto.OwnApkDeltaUpdateDto
import com.openclaw.tv.core.network.dto.OwnApkFullUpdateDto
import com.openclaw.tv.core.network.dto.OwnApkResourceUpdateDto
import com.openclaw.tv.core.network.dto.OwnApkUpdateFallbackDto
import com.openclaw.tv.core.network.dto.OwnApkUpdateManifestDto
import com.openclaw.tv.core.network.dto.OwnApkUpdatePolicyDto
import com.openclaw.tv.core.network.dto.OwnApkUpdateReportEnvelope
import com.openclaw.tv.core.network.dto.OwnApkUpdateReportRequestDto
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnApkUpdateAgentTest {

    @Test
    fun sync_prefers_resource_update_without_apk_install() = runTest {
        val repository = FakeRepository(
            manifest = manifest(
                resources = OwnApkResourceUpdateDto(
                    available = true,
                    releaseId = "res_1",
                    versionCode = 2,
                    resourceVersion = "manifest-2",
                ),
                fullApk = fullApk(),
                deltaApk = deltaApk(),
            ),
        )
        val agent = OwnApkUpdateAgent(
            repository = repository,
            currentVersionCodeProvider = { 10 },
        )

        val decision = agent.sync("session_token")

        assertTrue(decision is OwnApkUpdateDecision.ResourceOnly)
        assertEquals("res_1", repository.reports.single().releaseId)
        assertEquals("offered", repository.reports.single().status)
    }

    @Test
    fun sync_uses_delta_when_exact_version_matches_and_device_idle() = runTest {
        val repository = FakeRepository(
            manifest = manifest(
                deltaApk = deltaApk(from = 10, to = 11),
                fullApk = fullApk(versionCode = 11),
            ),
        )
        val agent = OwnApkUpdateAgent(
            repository = repository,
            currentVersionCodeProvider = { 10 },
            isIdleForLargeDownload = { true },
        )

        val decision = agent.sync("session_token")

        assertTrue(decision is OwnApkUpdateDecision.DeltaApk)
        assertEquals("delta_1", repository.reports.single().releaseId)
        assertEquals(11L, repository.reports.single().targetVersionCode)
    }

    @Test
    fun sync_falls_back_to_full_apk_when_delta_version_does_not_match() = runTest {
        val repository = FakeRepository(
            manifest = manifest(
                deltaApk = deltaApk(from = 9, to = 11),
                fullApk = fullApk(versionCode = 11),
            ),
        )
        val agent = OwnApkUpdateAgent(
            repository = repository,
            currentVersionCodeProvider = { 10 },
            isIdleForLargeDownload = { true },
        )

        val decision = agent.sync("session_token")

        assertTrue(decision is OwnApkUpdateDecision.FullApk)
        assertEquals("full_1", repository.reports.single().releaseId)
    }

    @Test
    fun sync_falls_back_to_full_apk_when_delta_algorithm_is_not_supported() = runTest {
        val repository = FakeRepository(
            manifest = manifest(
                deltaApk = deltaApk(from = 10, to = 11, algorithm = "bsdiff"),
                fullApk = fullApk(versionCode = 11),
            ),
        )
        val agent = OwnApkUpdateAgent(
            repository = repository,
            currentVersionCodeProvider = { 10 },
            isIdleForLargeDownload = { true },
        )

        val decision = agent.sync("session_token")

        assertTrue(decision is OwnApkUpdateDecision.FullApk)
        assertEquals("full_1", repository.reports.single().releaseId)
    }

    @Test
    fun sync_queues_full_apk_download_when_store_and_enqueuer_are_available() = runTest {
        val repository = FakeRepository(
            manifest = manifest(
                fullApk = fullApk(versionCode = 11),
            ),
        )
        val store = InMemoryOwnApkDownloadStore()
        val agent = OwnApkUpdateAgent(
            repository = repository,
            currentVersionCodeProvider = { 10 },
            isIdleForLargeDownload = { true },
            downloadStore = store,
            downloadEnqueuer = FakeOwnApkDownloadEnqueuer(),
            nowEpochMs = { 1234L },
        )

        val decision = agent.sync("session_token")

        assertTrue(decision is OwnApkUpdateDecision.FullApk)
        assertEquals("downloading", store.read()?.status)
        assertEquals(42L, store.read()?.downloadId)
        assertEquals("offered", repository.reports.first().status)
        assertEquals("downloading", repository.reports.last().status)
    }

    @Test
    fun sync_defers_large_updates_when_device_is_not_idle() = runTest {
        val repository = FakeRepository(
            manifest = manifest(
                deltaApk = deltaApk(from = 10, to = 11, patchSize = 64L * 1024L * 1024L),
                fullApk = fullApk(versionCode = 11, artifactSize = 64L * 1024L * 1024L),
            ),
        )
        val agent = OwnApkUpdateAgent(
            repository = repository,
            currentVersionCodeProvider = { 10 },
            isIdleForLargeDownload = { false },
        )

        val decision = agent.sync("session_token")

        assertEquals(OwnApkUpdateDecision.None, decision)
        assertEquals(emptyList<OwnApkUpdateReportRequestDto>(), repository.reports)
    }

    @Test
    fun sync_allows_small_full_apk_download_when_device_is_active() = runTest {
        val repository = FakeRepository(
            manifest = manifest(
                fullApk = fullApk(versionCode = 11, artifactSize = 13L * 1024L * 1024L),
            ),
        )
        val store = InMemoryOwnApkDownloadStore()
        val agent = OwnApkUpdateAgent(
            repository = repository,
            currentVersionCodeProvider = { 10 },
            isIdleForLargeDownload = { false },
            downloadStore = store,
            downloadEnqueuer = FakeOwnApkDownloadEnqueuer(),
        )

        val decision = agent.sync("session_token")

        assertTrue(decision is OwnApkUpdateDecision.FullApk)
        assertEquals("downloading", store.read()?.status)
        assertEquals(listOf("offered", "downloading"), repository.reports.map { it.status })
    }

    private fun manifest(
        resources: OwnApkResourceUpdateDto = OwnApkResourceUpdateDto(),
        fullApk: OwnApkFullUpdateDto = OwnApkFullUpdateDto(),
        deltaApk: OwnApkDeltaUpdateDto = OwnApkDeltaUpdateDto(),
    ) = OwnApkUpdateManifestDto(
        status = "ok",
        policy = OwnApkUpdatePolicyDto(minCheckIntervalSeconds = 21_600),
        resources = resources,
        fullApk = fullApk,
        deltaApk = deltaApk,
        fallback = OwnApkUpdateFallbackDto(fullApkReleaseId = fullApk.id),
    )

    private fun fullApk(
        versionCode: Long = 11,
        artifactSize: Long = 4096,
    ) = OwnApkFullUpdateDto(
        available = true,
        id = "full_1",
        versionCode = versionCode,
        artifactUrl = "https://cdn.example.com/openclaw.apk",
        artifactSha256 = "sha-full",
        artifactSize = artifactSize,
    )

    private fun deltaApk(
        from: Long = 10,
        to: Long = 11,
        algorithm: String = "full-copy",
        patchSize: Long = 4096,
    ) = OwnApkDeltaUpdateDto(
        available = true,
        id = "delta_1",
        fromVersionCode = from,
        toVersionCode = to,
        patchUrl = "https://cdn.example.com/openclaw.patch",
        patchSha256 = "sha-patch",
        patchSize = patchSize,
        targetApkSha256 = "sha-full",
        targetApkSize = 4096,
        algorithm = algorithm,
    )

    private class FakeRepository(
        private val manifest: OwnApkUpdateManifestDto,
    ) : OwnApkUpdateRepository {
        val reports = mutableListOf<OwnApkUpdateReportRequestDto>()

        override suspend fun fetchManifest(
            sessionToken: String,
            currentVersionCode: Long,
            currentResourceVersion: String?,
        ): OwnApkUpdateManifestDto = manifest

        override suspend fun report(
            sessionToken: String,
            request: OwnApkUpdateReportRequestDto,
        ): OwnApkUpdateReportEnvelope {
            reports += request
            return OwnApkUpdateReportEnvelope(status = "ok")
        }
    }

    private class FakeOwnApkDownloadEnqueuer : OwnApkDownloadEnqueuer {
        override suspend fun enqueue(request: OwnApkDownloadRequest): QueuedOwnApkDownload {
            return QueuedOwnApkDownload(
                downloadId = 42L,
                localFilePath = "/downloads/openclaw.apk",
            )
        }
    }
}
