package com.openclaw.tv.upgrade

class OwnApkAutoInstallCoordinator(
    private val store: OwnApkDownloadStore,
    private val installer: OwnApkSilentInstallSubmitter,
    private val currentVersionCodeProvider: () -> Long,
    private val isIdleForInstall: () -> Boolean,
    private val reportInstalling: suspend (
        sessionToken: String,
        update: StoredOwnApkUpdate,
        currentVersionCode: Long,
    ) -> Unit = { _, _, _ -> },
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
                reportInstallingSafely(
                    sessionToken = sessionToken,
                    update = installing,
                    currentVersionCode = currentVersionCode,
                )
                logInfo("Submitted own APK silent install releaseId=${update.releaseId} target=${update.targetVersionCode}")
                true
            }

            OwnApkInstallAttemptResult.PermissionRequired -> {
                logWarning(
                    "Skipped own APK silent install because INSTALL_PACKAGES is not granted releaseId=${update.releaseId}",
                    null,
                )
                false
            }

            OwnApkInstallAttemptResult.PromptLaunched -> {
                logWarning(
                    "Unexpected system installer prompt from silent own APK install releaseId=${update.releaseId}",
                    null,
                )
                false
            }

            is OwnApkInstallAttemptResult.Failed -> {
                logWarning(
                    "Failed to submit own APK silent install releaseId=${update.releaseId}: ${result.message}",
                    null,
                )
                false
            }
        }
    }

    private suspend fun reportInstallingSafely(
        sessionToken: String?,
        update: StoredOwnApkUpdate,
        currentVersionCode: Long,
    ) {
        val token = sessionToken?.trim()?.takeIf(String::isNotBlank) ?: return
        runCatching {
            reportInstalling(token, update, currentVersionCode)
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
