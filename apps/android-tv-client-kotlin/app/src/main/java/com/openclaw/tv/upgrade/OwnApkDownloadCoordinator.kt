package com.openclaw.tv.upgrade

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.openclaw.tv.core.network.dto.OwnApkUpdateReportRequestDto
import com.openclaw.tv.feature.appdelivery.AppChecksumVerifier
import com.openclaw.tv.feature.appdelivery.TrackedAppDownloadStatus
import com.openclaw.tv.feature.appdelivery.TrackedAppDownloadStatusResolver
import java.io.File
import kotlinx.coroutines.CancellationException

data class OwnApkDownloadRequest(
    val releaseId: String,
    val updateMode: String,
    val targetVersionCode: Long,
    val targetVersionName: String,
    val downloadUrl: String,
    val sha256: String,
    val size: Long,
    val installPolicy: String,
)

data class QueuedOwnApkDownload(
    val downloadId: Long,
    val localFilePath: String,
)

fun interface OwnApkDownloadEnqueuer {
    suspend fun enqueue(request: OwnApkDownloadRequest): QueuedOwnApkDownload
}

class DownloadManagerOwnApkDownloadEnqueuer(
    context: Context,
) : OwnApkDownloadEnqueuer {
    private val appContext = context.applicationContext
    private val downloadManager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    override suspend fun enqueue(request: OwnApkDownloadRequest): QueuedOwnApkDownload {
        val downloadsDir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(appContext.filesDir, "downloads")
        val destinationFile = File(downloadsDir, request.safeFileName())
        destinationFile.parentFile?.mkdirs()
        if (destinationFile.exists()) {
            destinationFile.delete()
        }
        val downloadId = downloadManager.enqueue(
            DownloadManager.Request(Uri.parse(request.downloadUrl)).apply {
                setTitle("OpenClaw TV ${request.targetVersionName.ifBlank { request.targetVersionCode.toString() }}")
                setDescription("正在后台下载系统更新")
                setDestinationUri(Uri.fromFile(destinationFile))
                setAllowedOverMetered(false)
                setAllowedOverRoaming(false)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            },
        )
        return QueuedOwnApkDownload(
            downloadId = downloadId,
            localFilePath = destinationFile.absolutePath,
        )
    }

    private fun OwnApkDownloadRequest.safeFileName(): String {
        val suffix = if (updateMode == "delta_apk") "patch" else "apk"
        val normalizedVersion = targetVersionName.ifBlank { targetVersionCode.toString() }
            .replace(InvalidFileNameChars, "_")
        return "openclaw-tv-$targetVersionCode-$normalizedVersion.$suffix"
    }

    private companion object {
        val InvalidFileNameChars = Regex("[^A-Za-z0-9._-]")
    }
}

class OwnApkDownloadCoordinator(
    private val store: OwnApkDownloadStore,
    private val repository: OwnApkUpdateRepository,
    private val statusResolver: TrackedAppDownloadStatusResolver,
    private val checksumVerifier: AppChecksumVerifier,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {

    suspend fun reconcile(sessionToken: String, currentVersionCode: Long): Boolean {
        val current = store.read() ?: return true
        if (current.targetVersionCode <= currentVersionCode && current.status in InstalledCandidateStatuses) {
            report(
                sessionToken = sessionToken,
                update = current,
                currentVersionCode = currentVersionCode,
                status = "installed",
                progressPercent = 100,
                note = null,
            )
            store.clear()
            return true
        }
        if (current.status in VerifiedCandidateStatuses) {
            return true
        }
        val downloadId = current.downloadId?.takeIf { it > 0L } ?: return true
        return when (val status = statusResolver.resolve(downloadId)) {
            is TrackedAppDownloadStatus.Pending -> {
                persistProgress(current, "downloading", status.downloadedBytes, status.totalBytes, null)
                true
            }

            is TrackedAppDownloadStatus.Running -> {
                persistProgress(current, "downloading", status.downloadedBytes, status.totalBytes, null)
                true
            }

            is TrackedAppDownloadStatus.Paused -> {
                persistProgress(current, "downloading", status.downloadedBytes, status.totalBytes, status.message)
                true
            }

            is TrackedAppDownloadStatus.Successful -> {
                verifySuccessfulDownload(
                    sessionToken = sessionToken,
                    currentVersionCode = currentVersionCode,
                    update = current,
                    localFilePath = status.localFilePath ?: current.localPath,
                )
            }

            is TrackedAppDownloadStatus.Failed -> {
                markFailed(
                    sessionToken = sessionToken,
                    currentVersionCode = currentVersionCode,
                    update = current,
                    message = status.message,
                )
                false
            }

            TrackedAppDownloadStatus.Missing -> {
                markFailed(
                    sessionToken = sessionToken,
                    currentVersionCode = currentVersionCode,
                    update = current,
                    message = "DownloadManager record not found",
                )
                false
            }
        }
    }

    suspend fun handleCompletedDownload(
        sessionToken: String?,
        downloadId: Long,
        currentVersionCode: Long,
    ): Boolean {
        val current = store.read()
            ?.takeIf { it.downloadId == downloadId }
            ?: return false
        if (sessionToken.isNullOrBlank()) {
            reconcileWithoutReport(current, downloadId)
            return true
        }
        reconcile(sessionToken, currentVersionCode)
        return true
    }

    private suspend fun verifySuccessfulDownload(
        sessionToken: String,
        currentVersionCode: Long,
        update: StoredOwnApkUpdate,
        localFilePath: String,
    ): Boolean {
        val downloaded = update.copy(
            status = "downloaded",
            localPath = localFilePath,
            progressPercent = 100,
            errorMessage = null,
            updatedAtEpochMs = nowEpochMs(),
        )
        store.write(downloaded)
        report(
            sessionToken = sessionToken,
            update = downloaded,
            currentVersionCode = currentVersionCode,
            status = "downloaded",
            progressPercent = 100,
            note = null,
        )

        return try {
            val actualSha256 = checksumVerifier.sha256(localFilePath)
            if (!actualSha256.equals(update.sha256, ignoreCase = true)) {
                markFailed(
                    sessionToken = sessionToken,
                    currentVersionCode = currentVersionCode,
                    update = downloaded,
                    message = "SHA-256 mismatch",
                )
                false
            } else {
                val verified = downloaded.copy(
                    status = "verified",
                    errorMessage = null,
                    updatedAtEpochMs = nowEpochMs(),
                )
                store.write(verified)
                report(
                    sessionToken = sessionToken,
                    update = verified,
                    currentVersionCode = currentVersionCode,
                    status = "verified",
                    progressPercent = 100,
                    note = null,
                )
                true
            }
        } catch (error: Exception) {
            if (error is CancellationException) {
                throw error
            }
            markFailed(
                sessionToken = sessionToken,
                currentVersionCode = currentVersionCode,
                update = downloaded,
                message = error.message ?: "Checksum verification failed",
            )
            false
        }
    }

    private suspend fun reconcileWithoutReport(update: StoredOwnApkUpdate, downloadId: Long) {
        when (val status = statusResolver.resolve(downloadId)) {
            is TrackedAppDownloadStatus.Successful -> {
                store.write(
                    update.copy(
                        status = "downloaded",
                        localPath = status.localFilePath ?: update.localPath,
                        progressPercent = 100,
                        errorMessage = null,
                        updatedAtEpochMs = nowEpochMs(),
                    ),
                )
            }

            is TrackedAppDownloadStatus.Failed -> {
                store.write(
                    update.copy(
                        status = "failed",
                        progressPercent = null,
                        errorMessage = status.message,
                        updatedAtEpochMs = nowEpochMs(),
                    ),
                )
            }

            TrackedAppDownloadStatus.Missing -> {
                store.write(
                    update.copy(
                        status = "failed",
                        progressPercent = null,
                        errorMessage = "DownloadManager record not found",
                        updatedAtEpochMs = nowEpochMs(),
                    ),
                )
            }

            is TrackedAppDownloadStatus.Pending,
            is TrackedAppDownloadStatus.Running,
            is TrackedAppDownloadStatus.Paused,
            -> Unit
        }
    }

    private suspend fun persistProgress(
        update: StoredOwnApkUpdate,
        status: String,
        downloadedBytes: Long,
        totalBytes: Long?,
        message: String?,
    ) {
        val progressPercent = totalBytes
            ?.takeIf { it > 0L }
            ?.let { ((downloadedBytes * 100L) / it).toInt().coerceIn(0, 99) }
        store.write(
            update.copy(
                status = status,
                progressPercent = progressPercent,
                errorMessage = message,
                updatedAtEpochMs = nowEpochMs(),
            ),
        )
    }

    private suspend fun markFailed(
        sessionToken: String,
        currentVersionCode: Long,
        update: StoredOwnApkUpdate,
        message: String,
    ) {
        val failed = update.copy(
            status = "failed",
            progressPercent = null,
            errorMessage = message,
            updatedAtEpochMs = nowEpochMs(),
        )
        store.write(failed)
        report(
            sessionToken = sessionToken,
            update = failed,
            currentVersionCode = currentVersionCode,
            status = "failed",
            progressPercent = null,
            note = message,
        )
    }

    private suspend fun report(
        sessionToken: String,
        update: StoredOwnApkUpdate,
        currentVersionCode: Long,
        status: String,
        progressPercent: Int?,
        note: String?,
    ) {
        repository.report(
            sessionToken = sessionToken,
            request = OwnApkUpdateReportRequestDto(
                releaseId = update.releaseId,
                currentVersionCode = currentVersionCode,
                targetVersionCode = update.targetVersionCode,
                status = status,
                progressPercent = progressPercent,
                note = note,
            ),
        )
    }

    private companion object {
        val InstalledCandidateStatuses = setOf(
            "downloaded",
            "verified",
            "prompt_shown",
            "installing",
            "installed",
        )
        val VerifiedCandidateStatuses = setOf(
            "verified",
            "prompt_shown",
            "installing",
            "installed",
        )
    }
}
