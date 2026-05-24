package com.openclaw.tv.upgrade

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.openclaw.tv.feature.appdelivery.resolveDownloadedOrLocalApkUri
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

sealed interface OwnApkInstallAttemptResult {
    data object SilentSubmitted : OwnApkInstallAttemptResult
    data object PromptLaunched : OwnApkInstallAttemptResult
    data object PermissionRequired : OwnApkInstallAttemptResult
    data class Failed(val message: String) : OwnApkInstallAttemptResult
}

class OwnApkUpdateInstaller(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val packageInstaller = appContext.packageManager.packageInstaller
    private val fileProviderAuthority = "${appContext.packageName}.fileprovider"

    fun install(update: StoredOwnApkUpdate): OwnApkInstallAttemptResult {
        val apkFile = update.localPath
            .trim()
            .takeIf(String::isNotBlank)
            ?.let(::File)
            ?.takeIf(File::exists)
            ?: return OwnApkInstallAttemptResult.Failed("安装包不存在，请等待重新下载。")

        if (canUseSilentInstall()) {
            val silentResult = runCatching {
                submitSilentInstall(update, apkFile)
            }.onFailure { error ->
                Log.w(TAG, "Silent own APK install failed releaseId=${update.releaseId}", error)
            }.getOrDefault(false)
            if (silentResult) {
                return OwnApkInstallAttemptResult.SilentSubmitted
            }
        }

        return openSystemInstaller(update)
    }

    fun openSystemInstaller(update: StoredOwnApkUpdate): OwnApkInstallAttemptResult {
        val apkUri = resolveApkUri(update)
            ?: return OwnApkInstallAttemptResult.Failed("安装包不可用，请等待重新下载。")
        if (!canRequestPackageInstalls()) {
            appContext.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${appContext.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
            return OwnApkInstallAttemptResult.PermissionRequired
        }

        return runCatching {
            appContext.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, APK_MIME_TYPE)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
            )
        }.fold(
            onSuccess = { OwnApkInstallAttemptResult.PromptLaunched },
            onFailure = { OwnApkInstallAttemptResult.Failed("当前设备无法打开系统安装器。") },
        )
    }

    fun canRequestPackageInstalls(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    private fun canUseSilentInstall(): Boolean {
        return appContext.packageManager.checkPermission(
            Manifest.permission.INSTALL_PACKAGES,
            appContext.packageName,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun submitSilentInstall(update: StoredOwnApkUpdate, apkFile: File): Boolean {
        val sessionId = packageInstaller.createSession(
            PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(appContext.packageName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
            },
        )
        packageInstaller.openSession(sessionId).use { session ->
            apkFile.inputStream().use { input ->
                session.openWrite("openclaw-own-apk", 0L, apkFile.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            session.commit(buildInstallResultIntentSender(update, sessionId))
        }
        return true
    }

    private fun buildInstallResultIntentSender(
        update: StoredOwnApkUpdate,
        sessionId: Int,
    ) = PendingIntent.getBroadcast(
        appContext,
        sessionId,
        Intent(appContext, OwnApkInstallResultReceiver::class.java).apply {
            action = ACTION_OWN_APK_INSTALL_RESULT
            putExtra(EXTRA_RELEASE_ID, update.releaseId)
            putExtra(EXTRA_TARGET_VERSION_CODE, update.targetVersionCode)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or packageInstallerPendingIntentFlag(),
    ).intentSender

    private fun resolveApkUri(update: StoredOwnApkUpdate): Uri? {
        return resolveDownloadedOrLocalApkUri(
            downloadedFileUri = null,
            localFilePath = update.localPath,
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
        const val TAG = "OpenClawUpgrade"
    }
}

class OwnApkInstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_OWN_APK_INSTALL_RESULT) {
            return
        }
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handleResult(context.applicationContext, intent)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleResult(context: Context, intent: Intent) {
        val releaseId = intent.getStringExtra(EXTRA_RELEASE_ID).orEmpty()
        val targetVersionCode = intent.getLongExtra(EXTRA_TARGET_VERSION_CODE, 0L)
        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE,
        )
        val store = SharedPreferencesOwnApkDownloadStore(context)
        val update = store.read()
            ?.takeIf { it.releaseId == releaseId && it.targetVersionCode == targetVersionCode }
            ?: return

        when (status) {
            PackageInstaller.STATUS_SUCCESS -> Unit

            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmationIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                runCatching {
                    if (confirmationIntent != null) {
                        context.startActivity(confirmationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } else {
                        OwnApkUpdateInstaller(context).openSystemInstaller(update)
                    }
                }.onFailure { error ->
                    Log.w(TAG, "Failed to launch pending user action for own APK install", error)
                }
            }

            else -> {
                store.write(
                    update.copy(
                        status = "verified",
                        errorMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                            ?: "Silent install failed",
                        updatedAtEpochMs = System.currentTimeMillis(),
                    ),
                )
                runCatching {
                    OwnApkUpdateInstaller(context).openSystemInstaller(update)
                }.onFailure { error ->
                    Log.w(TAG, "Failed to fallback to system installer for own APK update", error)
                }
            }
        }
    }

    private companion object {
        const val TAG = "OpenClawUpgrade"
    }
}

internal const val ACTION_OWN_APK_INSTALL_RESULT = "com.openclaw.tv.action.OWN_APK_INSTALL_RESULT"
internal const val EXTRA_RELEASE_ID = "release_id"
internal const val EXTRA_TARGET_VERSION_CODE = "target_version_code"

internal fun packageInstallerPendingIntentFlag(): Int {
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> PendingIntent.FLAG_MUTABLE
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> PendingIntent.FLAG_IMMUTABLE
        else -> 0
    }
}
