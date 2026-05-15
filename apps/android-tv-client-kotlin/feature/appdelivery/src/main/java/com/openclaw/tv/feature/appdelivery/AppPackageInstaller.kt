package com.openclaw.tv.feature.appdelivery

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

sealed interface AppInstallPromptResult {
    data object Launched : AppInstallPromptResult
    data object PermissionRequired : AppInstallPromptResult
    data class Failed(val message: String) : AppInstallPromptResult
}

class AppPackageInstaller(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val downloadManager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val fileProviderAuthority = "${appContext.packageName}.fileprovider"

    fun promptInstall(
        downloadId: Long?,
        localFilePath: String?,
    ): AppInstallPromptResult {
        val apkUri = resolveApkUri(downloadId, localFilePath)
            ?: return AppInstallPromptResult.Failed("安装包不存在，建议重新下载。")
        return promptInstall(apkUri)
    }

    fun promptInstall(apkUri: Uri): AppInstallPromptResult {
        if (!canRequestPackageInstalls()) {
            appContext.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${appContext.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
            return AppInstallPromptResult.PermissionRequired
        }

        runCatching {
            appContext.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, APK_MIME_TYPE)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
            )
        }.getOrElse {
            return AppInstallPromptResult.Failed("当前设备无法打开系统安装提示。")
        }
        return AppInstallPromptResult.Launched
    }

    fun canRequestPackageInstalls(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    private fun resolveApkUri(
        downloadId: Long?,
        localFilePath: String?,
    ): Uri? {
        val downloadedFileUri = downloadId
            ?.takeIf { it > 0L }
            ?.let(downloadManager::getUriForDownloadedFile)
        return resolveDownloadedOrLocalApkUri(
            downloadedFileUri = downloadedFileUri,
            localFilePath = localFilePath,
            localFileUriProvider = ::resolveLocalApkUri,
        )
    }

    private fun resolveLocalApkUri(file: File): Uri? {
        return runCatching {
            FileProvider.getUriForFile(appContext, fileProviderAuthority, file)
        }.getOrNull()
    }

    private companion object {
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}
