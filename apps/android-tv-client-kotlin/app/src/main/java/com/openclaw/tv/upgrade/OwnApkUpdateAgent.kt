package com.openclaw.tv.upgrade

import com.openclaw.tv.core.network.dto.OwnApkDeltaUpdateDto
import com.openclaw.tv.core.network.dto.OwnApkFullUpdateDto
import com.openclaw.tv.core.network.dto.OwnApkResourceUpdateDto
import com.openclaw.tv.core.network.dto.OwnApkUpdateReportRequestDto

sealed interface OwnApkUpdateDecision {
    data object None : OwnApkUpdateDecision
    data class ResourceOnly(val resource: OwnApkResourceUpdateDto) : OwnApkUpdateDecision
    data class FullApk(val fullApk: OwnApkFullUpdateDto) : OwnApkUpdateDecision
    data class DeltaApk(val deltaApk: OwnApkDeltaUpdateDto) : OwnApkUpdateDecision
}

class OwnApkUpdateAgent(
    private val repository: OwnApkUpdateRepository,
    private val currentVersionCodeProvider: () -> Long,
    private val currentResourceVersionProvider: () -> String? = { null },
    private val isIdleForLargeDownload: () -> Boolean = { true },
    private val downloadStore: OwnApkDownloadStore? = null,
    private val downloadEnqueuer: OwnApkDownloadEnqueuer? = null,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
    private val logInfo: (String) -> Unit = {},
    private val logWarning: (String, Throwable) -> Unit = { _, _ -> },
) {
    private var nextManifestCheckAtEpochMs: Long = 0L

    suspend fun sync(
        sessionToken: String,
    ): OwnApkUpdateDecision {
        val currentVersionCode = currentVersionCodeProvider().coerceAtLeast(0L)
        val now = nowEpochMs()
        if (now < nextManifestCheckAtEpochMs) {
            return OwnApkUpdateDecision.None
        }
        val manifest = repository.fetchManifest(
            sessionToken = sessionToken,
            currentVersionCode = currentVersionCode,
            currentResourceVersion = currentResourceVersionProvider(),
        )
        nextManifestCheckAtEpochMs = now + manifest.policy.minCheckIntervalSeconds
            .coerceAtLeast(MinManifestCheckIntervalSeconds)
            .times(1_000L)
        val decision = chooseDecision(
            currentVersionCode = currentVersionCode,
            resources = manifest.resources,
            fullApk = manifest.fullApk,
            deltaApk = manifest.deltaApk,
        )
        reportOfferedIfNeeded(
            sessionToken = sessionToken,
            currentVersionCode = currentVersionCode,
            decision = decision,
        )
        enqueueDownloadIfNeeded(
            sessionToken = sessionToken,
            currentVersionCode = currentVersionCode,
            decision = decision,
        )
        return decision
    }

    fun chooseDecision(
        currentVersionCode: Long,
        resources: OwnApkResourceUpdateDto,
        fullApk: OwnApkFullUpdateDto,
        deltaApk: OwnApkDeltaUpdateDto,
    ): OwnApkUpdateDecision {
        if (resources.available && resources.releaseId.isNotBlank()) {
            return OwnApkUpdateDecision.ResourceOnly(resources)
        }
        if (
            deltaApk.available &&
            deltaApk.id.isNotBlank() &&
            deltaApk.fromVersionCode == currentVersionCode &&
            deltaApk.toVersionCode > currentVersionCode &&
            deltaApk.patchUrl.startsWith("http") &&
            deltaApk.patchSha256.isNotBlank() &&
            deltaApk.targetApkSha256.isNotBlank() &&
            supportsDeltaAlgorithm(deltaApk.algorithm) &&
            canStartDownload(deltaApk.patchSize)
        ) {
            return OwnApkUpdateDecision.DeltaApk(deltaApk)
        }
        if (
            fullApk.available &&
            fullApk.id.isNotBlank() &&
            fullApk.versionCode > currentVersionCode &&
            fullApk.artifactUrl.startsWith("http") &&
            fullApk.artifactSha256.isNotBlank() &&
            fullApk.artifactSize > 0L &&
            canStartDownload(fullApk.artifactSize)
        ) {
            return OwnApkUpdateDecision.FullApk(fullApk)
        }
        return OwnApkUpdateDecision.None
    }

    private suspend fun reportOfferedIfNeeded(
        sessionToken: String,
        currentVersionCode: Long,
        decision: OwnApkUpdateDecision,
    ) {
        val request = when (decision) {
            OwnApkUpdateDecision.None -> return
            is OwnApkUpdateDecision.ResourceOnly -> OwnApkUpdateReportRequestDto(
                releaseId = decision.resource.releaseId,
                currentVersionCode = currentVersionCode,
                targetVersionCode = decision.resource.versionCode,
                status = "offered",
                progressPercent = 0,
            )
            is OwnApkUpdateDecision.FullApk -> OwnApkUpdateReportRequestDto(
                releaseId = decision.fullApk.id,
                currentVersionCode = currentVersionCode,
                targetVersionCode = decision.fullApk.versionCode,
                status = "offered",
                progressPercent = 0,
            )
            is OwnApkUpdateDecision.DeltaApk -> OwnApkUpdateReportRequestDto(
                releaseId = decision.deltaApk.id,
                currentVersionCode = currentVersionCode,
                targetVersionCode = decision.deltaApk.toVersionCode,
                status = "offered",
                progressPercent = 0,
            )
        }
        runCatching {
            repository.report(sessionToken, request)
        }.onSuccess {
            logInfo("Reported own APK update offer releaseId=${request.releaseId} target=${request.targetVersionCode}")
        }.onFailure { error ->
            logWarning("Failed to report own APK update offer releaseId=${request.releaseId}", error)
        }
    }

    private suspend fun enqueueDownloadIfNeeded(
        sessionToken: String,
        currentVersionCode: Long,
        decision: OwnApkUpdateDecision,
    ) {
        val store = downloadStore ?: return
        val enqueuer = downloadEnqueuer ?: return
        val request = when (decision) {
            OwnApkUpdateDecision.None,
            is OwnApkUpdateDecision.ResourceOnly,
            -> return

            is OwnApkUpdateDecision.FullApk -> OwnApkDownloadRequest(
                releaseId = decision.fullApk.id,
                updateMode = "full_apk",
                targetVersionCode = decision.fullApk.versionCode,
                targetVersionName = decision.fullApk.versionName,
                downloadUrl = decision.fullApk.artifactUrl,
                sha256 = decision.fullApk.artifactSha256,
                size = decision.fullApk.artifactSize,
                installPolicy = decision.fullApk.installPolicy,
            )

            is OwnApkUpdateDecision.DeltaApk -> OwnApkDownloadRequest(
                releaseId = decision.deltaApk.id,
                updateMode = "delta_apk",
                targetVersionCode = decision.deltaApk.toVersionCode,
                targetVersionName = decision.deltaApk.versionName,
                downloadUrl = decision.deltaApk.patchUrl,
                sha256 = decision.deltaApk.patchSha256,
                size = decision.deltaApk.patchSize,
                installPolicy = decision.deltaApk.installPolicy,
            )
        }
        if (request.downloadUrl.isBlank() || request.sha256.isBlank()) {
            return
        }
        val existing = store.read()
        if (
            existing?.releaseId == request.releaseId &&
            existing.targetVersionCode == request.targetVersionCode &&
            existing.status in DownloadAlreadyTrackedStatuses
        ) {
            return
        }
        runCatching {
            val queued = enqueuer.enqueue(request)
            val stored = StoredOwnApkUpdate(
                releaseId = request.releaseId,
                updateMode = request.updateMode,
                targetVersionCode = request.targetVersionCode,
                targetVersionName = request.targetVersionName,
                downloadUrl = request.downloadUrl,
                sha256 = request.sha256,
                size = request.size,
                installPolicy = request.installPolicy,
                downloadId = queued.downloadId,
                localPath = queued.localFilePath,
                status = "downloading",
                progressPercent = 0,
                errorMessage = null,
                updatedAtEpochMs = nowEpochMs(),
            )
            store.write(stored)
            repository.report(
                sessionToken = sessionToken,
                request = OwnApkUpdateReportRequestDto(
                    releaseId = request.releaseId,
                    currentVersionCode = currentVersionCode,
                    targetVersionCode = request.targetVersionCode,
                    status = "downloading",
                    progressPercent = 0,
                ),
            )
            logInfo("Queued own APK update download releaseId=${request.releaseId} target=${request.targetVersionCode}")
        }.onFailure { error ->
            logWarning("Failed to queue own APK update download releaseId=${request.releaseId}", error)
            store.write(
                StoredOwnApkUpdate(
                    releaseId = request.releaseId,
                    updateMode = request.updateMode,
                    targetVersionCode = request.targetVersionCode,
                    targetVersionName = request.targetVersionName,
                    downloadUrl = request.downloadUrl,
                    sha256 = request.sha256,
                    size = request.size,
                    installPolicy = request.installPolicy,
                    status = "failed",
                    errorMessage = error.message ?: "Failed to queue download",
                    updatedAtEpochMs = nowEpochMs(),
                ),
            )
            runCatching {
                repository.report(
                    sessionToken = sessionToken,
                    request = OwnApkUpdateReportRequestDto(
                        releaseId = request.releaseId,
                        currentVersionCode = currentVersionCode,
                        targetVersionCode = request.targetVersionCode,
                        status = "failed",
                        note = error.message ?: "Failed to queue download",
                    ),
                )
            }.onFailure { reportError ->
                logWarning("Failed to report own APK queue failure releaseId=${request.releaseId}", reportError)
            }
        }
    }

    private fun supportsDeltaAlgorithm(algorithm: String): Boolean {
        return algorithm.equals("full-copy", ignoreCase = true)
    }

    private fun canStartDownload(sizeBytes: Long): Boolean {
        return isIdleForLargeDownload() || sizeBytes in 1..ActiveDownloadMaxBytes
    }

    private companion object {
        const val MinManifestCheckIntervalSeconds = 300
        const val ActiveDownloadMaxBytes = 32L * 1024L * 1024L
        val DownloadAlreadyTrackedStatuses = setOf(
            "offered",
            "downloading",
            "downloaded",
            "verified",
            "installing",
            "installed",
        )
    }
}
