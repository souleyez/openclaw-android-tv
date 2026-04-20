package com.openclaw.tv.upgrade

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import com.openclaw.tv.feature.bootstrap.RuntimeUpgradeStatus

internal sealed interface UpgradeInstallState {
    data object Idle : UpgradeInstallState
    data class Downloading(
        val downloadId: Long,
        val progressPercent: Int? = null,
    ) : UpgradeInstallState
    data class Downloaded(val downloadId: Long, val apkUri: Uri) : UpgradeInstallState
    data class Installing(
        val downloadId: Long,
        val apkUri: Uri,
        val expectedVersion: String? = null,
    ) : UpgradeInstallState
    data class InstallPermissionRequired(val downloadId: Long, val apkUri: Uri) : UpgradeInstallState
    data class Failed(val message: String) : UpgradeInstallState
}

internal class ApkUpdateInstaller(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val downloadManager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    fun startDownload(upgrade: RuntimeUpgradeStatus): UpgradeInstallState {
        val artifactUrl = upgrade.artifactUrl
            ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            ?: return UpgradeInstallState.Failed("后台未提供可下载的升级包地址")
        val request = DownloadManager.Request(Uri.parse(artifactUrl)).apply {
            setTitle("OpenClaw TV ${upgrade.targetVersion ?: upgrade.latestVersion ?: "update"}")
            setDescription("正在下载升级包")
            setMimeType(APK_MIME_TYPE)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
            setDestinationInExternalFilesDir(
                appContext,
                Environment.DIRECTORY_DOWNLOADS,
                buildFileName(upgrade, artifactUrl),
            )
        }
        val downloadId = downloadManager.enqueue(request)
        Log.i(
            UPGRADE_TAG,
            "Queued upgrade download id=$downloadId targetVersion=${upgrade.targetVersion ?: upgrade.latestVersion ?: "unknown"}",
        )
        return UpgradeInstallState.Downloading(downloadId)
    }

    fun restore(downloadId: Long): UpgradeInstallState {
        if (downloadId <= 0L) {
            return UpgradeInstallState.Idle
        }
        val query = DownloadManager.Query().setFilterById(downloadId)
        downloadManager.query(query).use { cursor ->
            if (!cursor.moveToFirst()) {
                return UpgradeInstallState.Idle
            }
            return when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                DownloadManager.STATUS_PENDING,
                DownloadManager.STATUS_PAUSED,
                DownloadManager.STATUS_RUNNING,
                -> UpgradeInstallState.Downloading(
                    downloadId = downloadId,
                    progressPercent = readProgressPercent(cursor),
                )

                DownloadManager.STATUS_SUCCESSFUL -> {
                    val apkUri = downloadManager.getUriForDownloadedFile(downloadId)
                    if (apkUri != null) {
                        when {
                            canRequestPackageInstalls() -> UpgradeInstallState.Downloaded(downloadId, apkUri)
                            else -> UpgradeInstallState.InstallPermissionRequired(downloadId, apkUri)
                        }
                    } else {
                        UpgradeInstallState.Failed("升级包下载完成，但未找到安装文件")
                    }
                }

                DownloadManager.STATUS_FAILED -> UpgradeInstallState.Failed(
                    buildFailureMessage(
                        cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)),
                    ),
                )

                else -> UpgradeInstallState.Idle
            }
        }
    }

    fun openInstaller(
        apkUri: Uri,
        downloadId: Long? = null,
        expectedVersion: String? = null,
    ): UpgradeInstallState {
        if (!canRequestPackageInstalls()) {
            Log.w(
                UPGRADE_TAG,
                "Install permission required downloadId=${downloadId ?: -1L} expectedVersion=${expectedVersion ?: "unknown"}",
            )
            appContext.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${appContext.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
            return UpgradeInstallState.InstallPermissionRequired(
                downloadId = downloadId ?: -1L,
                apkUri = apkUri,
            )
        }

        Log.i(
            UPGRADE_TAG,
            "Opening system installer downloadId=${downloadId ?: -1L} expectedVersion=${expectedVersion ?: "unknown"}",
        )
        appContext.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, APK_MIME_TYPE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
        )
        return UpgradeInstallState.Installing(
            downloadId = downloadId ?: -1L,
            apkUri = apkUri,
            expectedVersion = expectedVersion,
        )
    }

    fun canRequestPackageInstalls(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    private fun buildFileName(
        upgrade: RuntimeUpgradeStatus,
        artifactUrl: String,
    ): String {
        val remoteName = Uri.parse(artifactUrl).lastPathSegment
            ?.substringAfterLast('/')
            ?.takeIf { it.endsWith(".apk") }
        if (!remoteName.isNullOrBlank()) {
            return remoteName
        }
        val version = upgrade.targetVersion ?: upgrade.latestVersion ?: "latest"
        return "openclaw-tv-$version.apk"
    }

    private fun buildFailureMessage(reason: Int): String {
        return when (reason) {
            DownloadManager.ERROR_CANNOT_RESUME -> "下载中断，无法继续恢复"
            DownloadManager.ERROR_DEVICE_NOT_FOUND -> "未找到可用存储设备"
            DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "升级包已存在，请重新尝试安装"
            DownloadManager.ERROR_FILE_ERROR -> "升级包文件写入失败"
            DownloadManager.ERROR_HTTP_DATA_ERROR -> "网络数据异常，下载失败"
            DownloadManager.ERROR_INSUFFICIENT_SPACE -> "存储空间不足，无法下载升级包"
            DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "下载地址重定向过多"
            DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "下载地址返回异常状态"
            DownloadManager.ERROR_UNKNOWN -> "升级包下载失败"
            else -> "升级包下载失败"
        }
    }

    private fun readProgressPercent(cursor: android.database.Cursor): Int? {
        val downloadedSoFar = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
        val totalSize = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
        if (downloadedSoFar < 0L || totalSize <= 0L) {
            return null
        }
        return ((downloadedSoFar * 100L) / totalSize)
            .toInt()
            .coerceIn(0, 100)
    }

    companion object {
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        private const val UPGRADE_TAG = "OpenClawUpgrade"
    }
}
