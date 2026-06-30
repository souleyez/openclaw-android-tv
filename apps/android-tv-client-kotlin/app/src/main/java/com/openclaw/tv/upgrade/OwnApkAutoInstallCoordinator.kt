package com.openclaw.tv.upgrade

class OwnApkAutoInstallCoordinator(
    private val store: OwnApkDownloadStore,
    private val installer: OwnApkSilentInstallSubmitter,
    private val currentVersionCodeProvider: () -> Long,
    private val isIdleForInstall: () -> Boolean,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
    private val logInfo: (String) -> Unit = {},
    private val logWarning: (String, Throwable?) -> Unit = { _, _ -> },
) {
    suspend fun maybeInstallVerifiedUpdate(): Boolean {
        val update = store.read() ?: return true
        if (!update.isAutoInstallCandidate(currentVersionCodeProvider().coerceAtLeast(0L))) {
            return true
        }
        if (!isIdleForInstall()) {
            return true
        }

        return when (val result = installer.installSilently(update)) {
            OwnApkInstallAttemptResult.SilentSubmitted -> {
                store.write(
                    update.copy(
                        status = "installing",
                        errorMessage = null,
                        updatedAtEpochMs = nowEpochMs(),
                    ),
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
