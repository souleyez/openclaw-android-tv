package com.openclaw.tv.feature.appdelivery

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

class DownloadManagerAppDownloadEnqueuer(
    context: Context,
) : AppDownloadEnqueuer {

    private val appContext = context.applicationContext
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
