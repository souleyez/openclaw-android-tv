package com.openclaw.tv.upgrade

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnApkAutoInstallCoordinatorTest {

    @Test
    fun maybeInstallVerifiedUpdate_submitsVendorSilentUpdateWhenIdle() = runTest {
        val store = InMemoryOwnApkDownloadStore(update(installPolicy = "vendor_silent"))
        val installer = FakeSilentInstaller(OwnApkInstallAttemptResult.SilentSubmitted)
        val reports = mutableListOf<InstallReport>()
        val coordinator = coordinator(store, installer, idle = true, reports = reports)

        val result = coordinator.maybeInstallVerifiedUpdate("session_token")

        assertTrue(result)
        assertEquals(1, installer.submitted.size)
        assertEquals("installing", store.read()?.status)
        assertEquals(200L, store.read()?.updatedAtEpochMs)
        assertEquals(
            listOf(InstallReport("session_token", "release_1", 10L, 11L, "installing", "silent install submitted")),
            reports,
        )
    }

    @Test
    fun maybeInstallVerifiedUpdate_keepsDeferredPromptManual() = runTest {
        val store = InMemoryOwnApkDownloadStore(update(installPolicy = "deferred_prompt"))
        val installer = FakeSilentInstaller(OwnApkInstallAttemptResult.SilentSubmitted)
        val coordinator = coordinator(store, installer, idle = true)

        val result = coordinator.maybeInstallVerifiedUpdate()

        assertTrue(result)
        assertEquals(0, installer.submitted.size)
        assertEquals("verified", store.read()?.status)
    }

    @Test
    fun maybeInstallVerifiedUpdate_waitsUntilIdle() = runTest {
        val store = InMemoryOwnApkDownloadStore(update(installPolicy = "system_staged"))
        val installer = FakeSilentInstaller(OwnApkInstallAttemptResult.SilentSubmitted)
        val coordinator = coordinator(store, installer, idle = false)

        val result = coordinator.maybeInstallVerifiedUpdate()

        assertTrue(result)
        assertEquals(0, installer.submitted.size)
        assertEquals("verified", store.read()?.status)
    }

    @Test
    fun maybeInstallVerifiedUpdate_reportsRecoverableFailureWhenSilentPermissionMissing() = runTest {
        val store = InMemoryOwnApkDownloadStore(update(installPolicy = "vendor_silent"))
        val installer = FakeSilentInstaller(OwnApkInstallAttemptResult.PermissionRequired)
        val reports = mutableListOf<InstallReport>()
        val coordinator = coordinator(store, installer, idle = true, reports = reports)

        val result = coordinator.maybeInstallVerifiedUpdate("session_token")

        assertEquals(false, result)
        assertEquals(1, installer.submitted.size)
        assertEquals("verified", store.read()?.status)
        assertEquals(
            "INSTALL_PACKAGES permission required; manual confirmation or factory permission required",
            store.read()?.errorMessage,
        )
        assertEquals(
            listOf(
                InstallReport(
                    "session_token",
                    "release_1",
                    10L,
                    11L,
                    "install_failed",
                    "INSTALL_PACKAGES permission required; manual confirmation or factory permission required",
                ),
            ),
            reports,
        )
    }

    @Test
    fun maybeInstallVerifiedUpdate_reportsSystemInstallerPrompt() = runTest {
        val store = InMemoryOwnApkDownloadStore(update(installPolicy = "vendor_silent"))
        val installer = FakeSilentInstaller(OwnApkInstallAttemptResult.PromptLaunched)
        val reports = mutableListOf<InstallReport>()
        val coordinator = coordinator(store, installer, idle = true, reports = reports)

        val result = coordinator.maybeInstallVerifiedUpdate("session_token")

        assertTrue(result)
        assertEquals(1, installer.submitted.size)
        assertEquals("prompt_shown", store.read()?.status)
        assertEquals(
            listOf(
                InstallReport(
                    "session_token",
                    "release_1",
                    10L,
                    11L,
                    "prompt_shown",
                    "system installer prompt launched; waiting for manual confirmation",
                ),
            ),
            reports,
        )
    }

    @Test
    fun maybeInstallVerifiedUpdate_reportsSubmitFailureWithoutDroppingRetryState() = runTest {
        val store = InMemoryOwnApkDownloadStore(update(installPolicy = "vendor_silent"))
        val installer = FakeSilentInstaller(OwnApkInstallAttemptResult.Failed("session creation failed"))
        val reports = mutableListOf<InstallReport>()
        val coordinator = coordinator(store, installer, idle = true, reports = reports)

        val result = coordinator.maybeInstallVerifiedUpdate("session_token")

        assertEquals(false, result)
        assertEquals(1, installer.submitted.size)
        assertEquals("verified", store.read()?.status)
        assertEquals("system installer retry required: session creation failed", store.read()?.errorMessage)
        assertEquals(
            listOf(
                InstallReport(
                    "session_token",
                    "release_1",
                    10L,
                    11L,
                    "install_failed",
                    "system installer retry required: session creation failed",
                ),
            ),
            reports,
        )
    }

    private fun coordinator(
        store: InMemoryOwnApkDownloadStore,
        installer: FakeSilentInstaller,
        idle: Boolean,
        reports: MutableList<InstallReport> = mutableListOf(),
    ) = OwnApkAutoInstallCoordinator(
        store = store,
        installer = installer,
        currentVersionCodeProvider = { 10 },
        isIdleForInstall = { idle },
        reportInstallStatus = { sessionToken, update, currentVersionCode, status, note ->
            reports += InstallReport(
                sessionToken = sessionToken,
                releaseId = update.releaseId,
                currentVersionCode = currentVersionCode,
                targetVersionCode = update.targetVersionCode,
                status = status,
                note = note,
            )
        },
        nowEpochMs = { 200L },
    )

    private fun update(
        installPolicy: String,
    ) = StoredOwnApkUpdate(
        releaseId = "release_1",
        updateMode = "full_apk",
        targetVersionCode = 11,
        targetVersionName = "0.1.14",
        localPath = "/downloads/openclaw.apk",
        status = "verified",
        installPolicy = installPolicy,
        updatedAtEpochMs = 100L,
    )

    private class FakeSilentInstaller(
        private val result: OwnApkInstallAttemptResult,
    ) : OwnApkSilentInstallSubmitter {
        val submitted = mutableListOf<StoredOwnApkUpdate>()

        override fun installSilently(update: StoredOwnApkUpdate): OwnApkInstallAttemptResult {
            submitted += update
            return result
        }
    }

    private data class InstallReport(
        val sessionToken: String,
        val releaseId: String,
        val currentVersionCode: Long,
        val targetVersionCode: Long,
        val status: String,
        val note: String?,
    )
}
