package com.openclaw.tv.feature.appdelivery

import android.net.Uri
import android.os.Build
import java.io.File

fun resolveDownloadedOrLocalApkUri(
    downloadedFileUri: Uri?,
    localFilePath: String?,
    sdkInt: Int = Build.VERSION.SDK_INT,
    localFileUriProvider: (File) -> Uri?,
): Uri? {
    downloadedFileUri?.let { return it }
    val localFile = localFilePath
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?.let(::File)
        ?.takeIf(File::exists)
        ?: return null
    return if (sdkInt >= Build.VERSION_CODES.N) {
        localFileUriProvider(localFile)
    } else {
        Uri.fromFile(localFile)
    }
}
