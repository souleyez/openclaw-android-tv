package com.openclaw.tv.feature.appdelivery

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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

    fun promptInstall(
        downloadId: Long?,
        localFilePath: String?,
    ): AppInstallPromptResult {
        val apkUri = resolveApkUri(downloadId, localFilePath)
            ?: return AppInstallPromptResult.Failed("安装包不存在，建议重新下载。")
        if (!canRequestPackageInstalls()) {
            appContext.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${appContext.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
            return AppInstallPromptResult.PermissionRequired
        }

        appContext.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, APK_MIME_TYPE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
        )
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
        val resolvedDownloadId = downloadId?.takeIf { it > 0L }
        if (resolvedDownloadId != null) {
            downloadManager.getUriForDownloadedFile(resolvedDownloadId)?.let { return it }
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            val file = localFilePath
                ?.takeIf(String::isNotBlank)
                ?.let(::File)
                ?.takeIf(File::exists)
                ?: return null
            return Uri.fromFile(file)
        }
        return null
    }

    private companion object {
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}
