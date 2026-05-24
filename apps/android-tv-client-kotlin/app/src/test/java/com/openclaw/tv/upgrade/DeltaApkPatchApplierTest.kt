package com.openclaw.tv.upgrade

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DeltaApkPatchApplierTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun full_copy_patch_reconstructs_target_and_verifies_hashes() {
        val patchFile = temporaryFolder.newFile("target-as-patch.apk")
        patchFile.writeText("target apk bytes")
        val outputFile = File(temporaryFolder.root, "out/target.apk")
        val sha = sha256(patchFile)

        val result = DeltaApkPatchApplier().apply(
            DeltaApkPatchRequest(
                patchFile = patchFile,
                outputApkFile = outputFile,
                patchSha256 = sha,
                targetApkSha256 = sha,
                algorithm = "full-copy",
            ),
        )

        assertTrue(result is DeltaApkPatchResult.Success)
        assertEquals("target apk bytes", outputFile.readText())
    }

    @Test
    fun mismatched_patch_hash_fails_before_writing_output() {
        val patchFile = temporaryFolder.newFile("target-as-patch.apk")
        patchFile.writeText("target apk bytes")
        val outputFile = File(temporaryFolder.root, "out/target.apk")

        val result = DeltaApkPatchApplier().apply(
            DeltaApkPatchRequest(
                patchFile = patchFile,
                outputApkFile = outputFile,
                patchSha256 = "bad-sha",
                targetApkSha256 = sha256(patchFile),
                algorithm = "full-copy",
            ),
        )

        assertEquals(DeltaApkPatchResult.Failed("PATCH_SHA256_MISMATCH"), result)
        assertEquals(false, outputFile.exists())
    }

    @Test
    fun unsupported_algorithm_is_reported_for_full_apk_fallback() {
        val patchFile = temporaryFolder.newFile("target.patch")
        patchFile.writeText("patch bytes")
        val outputFile = File(temporaryFolder.root, "out/target.apk")

        val result = DeltaApkPatchApplier().apply(
            DeltaApkPatchRequest(
                patchFile = patchFile,
                outputApkFile = outputFile,
                patchSha256 = sha256(patchFile),
                targetApkSha256 = "unused",
                algorithm = "archive-diff",
            ),
        )

        assertEquals(DeltaApkPatchResult.Failed("PATCH_ALGORITHM_UNSUPPORTED"), result)
    }

    private fun sha256(file: File): String {
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
}
