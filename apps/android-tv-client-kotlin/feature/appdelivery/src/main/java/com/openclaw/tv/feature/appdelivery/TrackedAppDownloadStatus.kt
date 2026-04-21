package com.openclaw.tv.feature.appdelivery

import android.app.DownloadManager
import android.content.Context
import android.net.Uri

sealed interface TrackedAppDownloadStatus {
    data class Pending(
        val downloadedBytes: Long = 0L,
        val totalBytes: Long? = null,
    ) : TrackedAppDownloadStatus

    data class Running(
        val downloadedBytes: Long,
        val totalBytes: Long? = null,
    ) : TrackedAppDownloadStatus

    data class Paused(
        val message: String,
        val downloadedBytes: Long,
        val totalBytes: Long? = null,
    ) : TrackedAppDownloadStatus

    data class Successful(
        val localFilePath: String?,
    ) : TrackedAppDownloadStatus

    data class Failed(
        val message: String,
    ) : TrackedAppDownloadStatus

    data object Missing : TrackedAppDownloadStatus
}

fun interface TrackedAppDownloadStatusResolver {
    fun resolve(downloadId: Long): TrackedAppDownloadStatus
}

class DownloadManagerTrackedAppDownloadStatusResolver(
    context: Context,
) : TrackedAppDownloadStatusResolver {
    private val appContext = context.applicationContext
    private val downloadManager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    override fun resolve(downloadId: Long): TrackedAppDownloadStatus {
        val query = DownloadManager.Query().setFilterById(downloadId)
        downloadManager.query(query).use { cursor ->
            if (!cursor.moveToFirst()) {
                return TrackedAppDownloadStatus.Missing
            }
            val downloadedBytes = cursor.readLongColumn(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val totalBytes = cursor.readLongColumn(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                ?.takeIf { it > 0L }
            return when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                DownloadManager.STATUS_PENDING -> TrackedAppDownloadStatus.Pending(
                    downloadedBytes = downloadedBytes ?: 0L,
                    totalBytes = totalBytes,
                )

                DownloadManager.STATUS_RUNNING -> TrackedAppDownloadStatus.Running(
                    downloadedBytes = downloadedBytes ?: 0L,
                    totalBytes = totalBytes,
                )

                DownloadManager.STATUS_PAUSED -> TrackedAppDownloadStatus.Paused(
                    message = describePauseReason(
                        cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)),
                    ),
                    downloadedBytes = downloadedBytes ?: 0L,
                    totalBytes = totalBytes,
                )

                DownloadManager.STATUS_SUCCESSFUL -> TrackedAppDownloadStatus.Successful(
                    localFilePath = cursor.readDownloadedFilePath(),
                )

                DownloadManager.STATUS_FAILED -> TrackedAppDownloadStatus.Failed(
                    message = describeFailureReason(
                        cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)),
                    ),
                )

                else -> TrackedAppDownloadStatus.Running(
                    downloadedBytes = downloadedBytes ?: 0L,
                    totalBytes = totalBytes,
                )
            }
        }
    }

    private fun android.database.Cursor.readDownloadedFilePath(): String? {
        val localUri = getString(getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: return null
        val parsedUri = Uri.parse(localUri)
        return when (parsedUri.scheme?.lowercase()) {
            null,
            "",
            "file",
            -> parsedUri.path

            else -> null
        }?.trim()?.takeIf(String::isNotBlank)
    }

    private fun android.database.Cursor.readLongColumn(columnName: String): Long? {
        val columnIndex = getColumnIndex(columnName)
        if (columnIndex < 0 || isNull(columnIndex)) {
            return null
        }
        return getLong(columnIndex)
    }

    private fun describePauseReason(reasonCode: Int): String {
        return when (reasonCode) {
            DownloadManager.PAUSED_QUEUED_FOR_WIFI -> "等待 Wi-Fi 后继续下载"
            DownloadManager.PAUSED_WAITING_FOR_NETWORK -> "等待网络恢复后继续下载"
            DownloadManager.PAUSED_WAITING_TO_RETRY -> "系统正在等待后续重试"
            DownloadManager.PAUSED_UNKNOWN -> "下载已暂停，等待系统恢复"
            else -> "下载已暂停，等待系统恢复"
        }
    }

    private fun describeFailureReason(reasonCode: Int): String {
        return when (reasonCode) {
            DownloadManager.ERROR_CANNOT_RESUME -> "下载中断，系统无法继续恢复"
            DownloadManager.ERROR_DEVICE_NOT_FOUND -> "没有可用的存储设备"
            DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "下载文件已存在"
            DownloadManager.ERROR_FILE_ERROR -> "下载文件写入失败"
            DownloadManager.ERROR_HTTP_DATA_ERROR -> "下载地址返回了异常数据"
            DownloadManager.ERROR_INSUFFICIENT_SPACE -> "设备存储空间不足"
            DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "下载地址重定向过多"
            DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "下载地址返回了异常状态"
            else -> "下载失败"
        }
    }
}
