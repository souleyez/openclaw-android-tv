package com.openclaw.tv.upgrade

class OwnApkAutoInstallCoordinator(
    private val store: OwnApkDownloadStore,
    private val installer: OwnApkSilentInstallSubmitter,
    private val currentVersionCodeProvider: () -> Long,
    private val isIdleForInstall: () -> Boolean,
    private val reportInstallStatus: suspend (
        sessionToken: String,
        update: StoredOwnApkUpdate,
        currentVersionCode: Long,
        status: String,
        note: String?,
    ) -> Unit = { _, _, _, _, _ -> },
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
    private val logInfo: (String) -> Unit = {},
    private val logWarning: (String, Throwable?) -> Unit = { _, _ -> },
) {
    suspend fun maybeInstallVerifiedUpdate(sessionToken: String? = null): Boolean {
        val currentVersionCode = currentVersionCodeProvider().coerceAtLeast(0L)
        val update = store.read() ?: return true
        if (!update.isAutoInstallCandidate(currentVersionCode)) {
            return true
        }
        if (!isIdleForInstall()) {
            return true
        }

        return when (val result = installer.installSilently(update)) {
            OwnApkInstallAttemptResult.SilentSubmitted -> {
                val installing = update.copy(
                    status = "installing",
                    errorMessage = null,
                    updatedAtEpochMs = nowEpochMs(),
                )
                store.write(installing)
                reportInstallStatusSafely(
                    sessionToken = sessionToken,
                    update = installing,
                    currentVersionCode = currentVersionCode,
                    status = "installing",
                    note = "silent install submitted",
                )
                logInfo("Submitted own APK silent install releaseId=${update.releaseId} target=${update.targetVersionCode}")
                true
            }

            OwnApkInstallAttemptResult.PermissionRequired -> {
                val note = "INSTALL_PACKAGES permission required; manual confirmation or factory permission required"
                val recoverable = update.copy(
                    errorMessage = note,
                    updatedAtEpochMs = nowEpochMs(),
                )
                store.write(recoverable)
                reportInstallStatusSafely(
                    sessionToken = sessionToken,
                    update = recoverable,
                    currentVersionCode = currentVersionCode,
                    status = "install_failed",
                    note = note,
                )
                logWarning(
                    "Skipped own APK silent install because INSTALL_PACKAGES is not granted releaseId=${update.releaseId}",
                    null,
                )
                false
            }

            OwnApkInstallAttemptResult.PromptLaunched -> {
                val prompted = update.copy(
                    status = "prompt_shown",
                    errorMessage = null,
                    updatedAtEpochMs = nowEpochMs(),
                )
                store.write(prompted)
                reportInstallStatusSafely(
                    sessionToken = sessionToken,
                    update = prompted,
                    currentVersionCode = currentVersionCode,
                    status = "prompt_shown",
                    note = "system installer prompt launched; waiting for manual confirmation",
                )
                logWarning(
                    "Unexpected system installer prompt from silent own APK install releaseId=${update.releaseId}",
                    null,
                )
                true
            }

            is OwnApkInstallAttemptResult.Failed -> {
                val note = "system installer retry required: ${result.message.ifBlank { "own APK install submission failed" }}"
                val recoverable = update.copy(
                    errorMessage = note,
                    updatedAtEpochMs = nowEpochMs(),
                )
                store.write(recoverable)
                reportInstallStatusSafely(
                    sessionToken = sessionToken,
                    update = recoverable,
                    currentVersionCode = currentVersionCode,
                    status = "install_failed",
                    note = note,
                )
                logWarning(
                    "Failed to submit own APK silent install releaseId=${update.releaseId}: ${result.message}",
                    null,
                )
                false
            }
        }
    }

    private suspend fun reportInstallStatusSafely(
        sessionToken: String?,
        update: StoredOwnApkUpdate,
        currentVersionCode: Long,
        status: String,
        note: String?,
    ) {
        val token = sessionToken?.trim()?.takeIf(String::isNotBlank) ?: return
        runCatching {
            reportInstallStatus(token, update, currentVersionCode, status, note)
        }.onFailure { error ->
            logWarning(
                "Failed to report own APK install submission releaseId=${update.releaseId}",
                error,
            )
        }
    }

    private fun StoredOwnApkUpdate.isAutoInstallCandidate(currentVersionCode: Long): Boolean {
        return targetVersionCode > currentVersionCode &&
            releaseId.isNotBlank() &&
            localPath.isNotBlank() &&
            status == "verified" &&
            installPolicy.normalizedAutoInstallPolicy()
    }

    private fun String.normalizedAutoInstallPolicy(): Boolean {
        return trim().lowercase() in AutoInstallPolicies
    }

    private companion object {
        val AutoInstallPolicies = setOf("vendor_silent", "system_staged")
    }
}
