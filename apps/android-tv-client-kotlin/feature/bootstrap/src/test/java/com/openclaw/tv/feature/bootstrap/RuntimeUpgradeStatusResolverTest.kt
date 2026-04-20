package com.openclaw.tv.feature.bootstrap

import com.openclaw.tv.core.network.dto.ClientPolicyDto
import com.openclaw.tv.core.network.dto.ReleaseDto
import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeUpgradeStatusResolverTest {

    @Test
    fun resolve_marks_required_when_current_version_is_below_min_supported() {
        val status = RuntimeUpgradeStatusResolver.resolve(
            currentClientVersion = "0.1.0",
            policy = policy(
                minSupportedVersion = "0.2.0",
                targetVersion = "0.3.0",
                forceUpgrade = false,
            ),
            release = release(
                version = "0.3.0",
                minSupportedVersion = "0.2.0",
            ),
        )

        assertEquals(RuntimeUpgradeState.REQUIRED, status?.state)
    }

    @Test
    fun resolve_marks_available_when_target_version_is_newer_but_still_supported() {
        val status = RuntimeUpgradeStatusResolver.resolve(
            currentClientVersion = "0.2.0",
            policy = policy(
                minSupportedVersion = "0.1.0",
                targetVersion = "0.3.0",
                forceUpgrade = false,
            ),
            release = release(
                version = "0.3.0",
                minSupportedVersion = "0.1.0",
            ),
        )

        assertEquals(RuntimeUpgradeState.AVAILABLE, status?.state)
    }

    @Test
    fun resolve_marks_current_when_local_version_matches_latest_release() {
        val status = RuntimeUpgradeStatusResolver.resolve(
            currentClientVersion = "0.3.0",
            policy = policy(
                minSupportedVersion = "0.1.0",
                targetVersion = "0.3.0",
                forceUpgrade = false,
            ),
            release = release(
                version = "0.3.0",
                minSupportedVersion = "0.1.0",
            ),
        )

        assertEquals(RuntimeUpgradeState.CURRENT, status?.state)
    }

    private fun policy(
        minSupportedVersion: String,
        targetVersion: String,
        forceUpgrade: Boolean,
    ): ClientPolicyDto {
        return ClientPolicyDto(
            channel = "stable",
            minSupportedVersion = minSupportedVersion,
            targetVersion = targetVersion,
            forceUpgrade = forceUpgrade,
            allowSelfRegister = true,
            modelAccessMode = "lease",
            providerScopes = listOf("moonshot"),
            defaultModel = "moonshot-v1",
            allowedModels = listOf("moonshot-v1"),
        )
    }

    private fun release(
        version: String,
        minSupportedVersion: String,
    ): ReleaseDto {
        return ReleaseDto(
            id = "release_1",
            projectKey = "openclaw",
            channel = "stable",
            version = version,
            status = "published",
            artifactType = "apk",
            artifactUrl = "https://cdn.example.com/app.apk",
            artifactSha256 = "sha256",
            artifactSize = 1024,
            runtimeVersion = version,
            releaseMetadata = emptyMap(),
            openclawVersion = version,
            installerVersion = "1",
            minSupportedVersion = minSupportedVersion,
            releaseNotes = "notes",
            publishedAt = "2026-04-19T00:00:00.000Z",
            createdAt = "2026-04-19T00:00:00.000Z",
            updatedAt = "2026-04-19T00:00:00.000Z",
        )
    }
}
