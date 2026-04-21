package com.openclaw.tv.runtime

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.PowerManager
import com.openclaw.tv.core.storage.AppDownloadStore
import com.openclaw.tv.feature.appdelivery.AppChecksumVerifier
import com.openclaw.tv.feature.appdelivery.AppDownloadCoordinator
import com.openclaw.tv.feature.appdelivery.AppDownloadEnqueuer
import com.openclaw.tv.feature.appdelivery.AppDownloadRequest
import com.openclaw.tv.feature.appdelivery.QueuedAppDownload
import com.openclaw.tv.feature.appdelivery.RuntimeManifestSnapshot
import com.openclaw.tv.feature.home.ResolvedTvHomeConfig
import com.openclaw.tv.feature.runtime.ResourceSessionCoordinator
import com.openclaw.tv.feature.runtime.ResourceSessionRepository
import com.openclaw.tv.feature.runtime.RuntimeEntitlementRepository
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

class RuntimeEntitlementSyncAdapter(
    private val repository: RuntimeEntitlementRepository,
) : RuntimeEntitlementSync {

    override suspend fun load(sessionToken: String) {
        repository.load(sessionToken)
    }

    override suspend fun clear() {
        repository.clear()
    }
}

class ResourceSessionRuntimeSyncAdapter(
    private val repository: ResourceSessionRepository,
    private val coordinator: ResourceSessionCoordinator,
) : ResourceSessionRuntimeSync {

    override suspend fun resume() {
        coordinator.resume()
    }

    override suspend fun hasStoredSession(): Boolean {
        return repository.getStoredResourceSession() != null
    }

    override suspend fun poll() {
        coordinator.poll()
    }

    override suspend fun clear() {
        repository.clearLocalResourceSession()
        coordinator.resume()
    }
}

class AppDeliveryRuntimeSyncAdapter(
    private val coordinator: AppDownloadCoordinator,
) : AppDeliveryRuntimeSync {

    override suspend fun enqueueEligibleDownloads(
        manifest: RuntimeManifestSnapshot,
        config: ResolvedTvHomeConfig,
        deviceIsActive: Boolean,
    ) {
        val resolvedManifest = manifest.manifest ?: return
        coordinator.enqueueEligibleDownloads(
            manifest = resolvedManifest,
            backgroundDownloadEnabled = config.backgroundDownloadEnabled,
            idleDownloadOnly = config.idleDownloadOnly,
            deviceIsActive = deviceIsActive,
        )
    }
}

class SystemDeviceActivityProvider(
    private val appContext: Context,
) : DeviceActivityProvider {

    override fun isDeviceActive(): Boolean {
        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isInteractive != false
    }
}

class DownloadManagerAppDownloadEnqueuer(
    private val appContext: Context,
) : AppDownloadEnqueuer {

    private val downloadManager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    override suspend fun enqueue(request: AppDownloadRequest): QueuedAppDownload {
        val safeFileName = buildSafeFileName(request)
        val downloadDirectory = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(appContext.filesDir, "downloads")
        val destinationFile = File(
            downloadDirectory,
            safeFileName,
        )
        destinationFile.parentFile?.mkdirs()
        if (destinationFile.exists()) {
            destinationFile.delete()
        }
        val downloadId = downloadManager.enqueue(
            DownloadManager.Request(Uri.parse(request.downloadUrl)).apply {
                setTitle(request.title)
                setDescription("${request.packageName} ${request.versionName}")
                setDestinationUri(Uri.fromFile(destinationFile))
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            },
        )
        return QueuedAppDownload(
            downloadId = downloadId,
            localFilePath = destinationFile.absolutePath,
        )
    }

    private fun buildSafeFileName(request: AppDownloadRequest): String {
        val normalizedAppId = request.appId.replace(InvalidFileNameChars, "_")
        val normalizedVersion = request.versionName.replace(InvalidFileNameChars, "_")
        return "$normalizedAppId-${request.versionCode}-$normalizedVersion.apk"
    }

    private companion object {
        val InvalidFileNameChars = Regex("[^A-Za-z0-9._-]")
    }
}

class FileSha256ChecksumVerifier : AppChecksumVerifier {
    override suspend fun sha256(localFilePath: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(localFilePath).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val bytesRead = input.read(buffer)
                if (bytesRead <= 0) {
                    break
                }
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString(separator = "") { byte ->
            "%02x".format(byte)
        }
    }
}

class AppDownloadCompletionTracker(
    private val downloadStore: AppDownloadStore,
    private val coordinator: AppDownloadCoordinator,
) {

    suspend fun handleCompletedDownload(downloadId: Long) {
        val matchedDownload = downloadStore.readAll()
            .values
            .firstOrNull { it.downloadId == downloadId }
            ?: return
        val localFilePath = matchedDownload.localFilePath?.takeIf(String::isNotBlank) ?: return
        coordinator.completeDownload(
            appId = matchedDownload.appId,
            localFilePath = localFilePath,
        )
    }
}
