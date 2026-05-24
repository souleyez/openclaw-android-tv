package com.openclaw.tv.upgrade

import java.io.File
import java.security.MessageDigest

data class DeltaApkPatchRequest(
    val patchFile: File,
    val outputApkFile: File,
    val patchSha256: String,
    val targetApkSha256: String,
    val algorithm: String,
)

sealed interface DeltaApkPatchResult {
    data class Success(val outputApkFile: File) : DeltaApkPatchResult
    data class Failed(val reason: String) : DeltaApkPatchResult
}

class DeltaApkPatchApplier(
    private val sha256: (File) -> String = ::calculateSha256,
) {

    fun apply(request: DeltaApkPatchRequest): DeltaApkPatchResult {
        if (!request.patchFile.isFile) {
            return DeltaApkPatchResult.Failed("PATCH_FILE_MISSING")
        }
        if (!request.patchSha256.equals(sha256(request.patchFile), ignoreCase = true)) {
            return DeltaApkPatchResult.Failed("PATCH_SHA256_MISMATCH")
        }

        return when (request.algorithm.trim().lowercase()) {
            // Useful for contract tests and emergency "delta" metadata that already points to a full target.
            "full-copy" -> copyPatchAsTarget(request)
            else -> DeltaApkPatchResult.Failed("PATCH_ALGORITHM_UNSUPPORTED")
        }
    }

    private fun copyPatchAsTarget(request: DeltaApkPatchRequest): DeltaApkPatchResult {
        request.outputApkFile.parentFile?.mkdirs()
        request.patchFile.copyTo(request.outputApkFile, overwrite = true)
        if (!request.targetApkSha256.equals(sha256(request.outputApkFile), ignoreCase = true)) {
            request.outputApkFile.delete()
            return DeltaApkPatchResult.Failed("TARGET_SHA256_MISMATCH")
        }
        return DeltaApkPatchResult.Success(request.outputApkFile)
    }
}

private fun calculateSha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) {
                break
            }
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}
