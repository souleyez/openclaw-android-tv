package com.openclaw.tv.feature.bootstrap

import com.openclaw.tv.core.network.dto.ClientPolicyDto
import com.openclaw.tv.core.network.dto.ReleaseDto

enum class RuntimeUpgradeState {
    CURRENT,
    AVAILABLE,
    REQUIRED,
}

data class RuntimeUpgradeStatus(
    val state: RuntimeUpgradeState,
    val currentVersion: String,
    val channel: String? = null,
    val targetVersion: String? = null,
    val latestVersion: String? = null,
    val minSupportedVersion: String? = null,
    val forceUpgrade: Boolean = false,
    val artifactUrl: String? = null,
)

internal object RuntimeUpgradeStatusResolver {

    fun resolve(
        currentClientVersion: String,
        policy: ClientPolicyDto?,
        release: ReleaseDto?,
    ): RuntimeUpgradeStatus? {
        if (currentClientVersion.isBlank() || (policy == null && release == null)) {
            return null
        }

        val targetVersion = release?.version?.takeIf(String::isNotBlank)
            ?: policy?.targetVersion?.takeIf(String::isNotBlank)
        val latestVersion = release?.version?.takeIf(String::isNotBlank) ?: targetVersion
        val minSupportedVersion = release?.minSupportedVersion?.takeIf(String::isNotBlank)
            ?: policy?.minSupportedVersion?.takeIf(String::isNotBlank)
        val forceUpgrade = policy?.forceUpgrade == true
        val isBelowMinimum = minSupportedVersion?.let {
            compareVersions(currentClientVersion, it) < 0
        } == true
        val isNewerVersionPublished = latestVersion?.let {
            compareVersions(currentClientVersion, it) < 0
        } == true

        return RuntimeUpgradeStatus(
            state = when {
                forceUpgrade || isBelowMinimum -> RuntimeUpgradeState.REQUIRED
                isNewerVersionPublished -> RuntimeUpgradeState.AVAILABLE
                else -> RuntimeUpgradeState.CURRENT
            },
            currentVersion = currentClientVersion,
            channel = release?.channel ?: policy?.channel,
            targetVersion = targetVersion,
            latestVersion = latestVersion,
            minSupportedVersion = minSupportedVersion,
            forceUpgrade = forceUpgrade,
            artifactUrl = release?.artifactUrl?.takeIf(String::isNotBlank),
        )
    }

    private fun compareVersions(
        left: String,
        right: String,
    ): Int {
        val leftTokens = tokenize(left)
        val rightTokens = tokenize(right)
        val maxTokenCount = maxOf(leftTokens.size, rightTokens.size)
        repeat(maxTokenCount) { index ->
            val comparison = compareTokens(
                left = leftTokens.getOrNull(index),
                right = rightTokens.getOrNull(index),
            )
            if (comparison != 0) {
                return comparison
            }
        }
        return 0
    }

    private fun tokenize(version: String): List<String> {
        return VERSION_TOKEN_REGEX.findAll(version.lowercase())
            .map { it.value }
            .toList()
            .ifEmpty { listOf(version.lowercase()) }
    }

    private fun compareTokens(
        left: String?,
        right: String?,
    ): Int {
        if (left == null && right == null) {
            return 0
        }
        if (left == null) {
            return compareMissingAgainst(right.orEmpty())
        }
        if (right == null) {
            return -compareMissingAgainst(left)
        }

        val leftIsNumber = left.all(Char::isDigit)
        val rightIsNumber = right.all(Char::isDigit)
        return when {
            leftIsNumber && rightIsNumber -> left.toBigInteger().compareTo(right.toBigInteger())
            leftIsNumber -> 1
            rightIsNumber -> -1
            else -> left.compareTo(right)
        }
    }

    private fun compareMissingAgainst(other: String): Int {
        return if (other.all(Char::isDigit)) {
            0.toBigInteger().compareTo(other.toBigInteger())
        } else {
            1
        }
    }

    private val VERSION_TOKEN_REGEX = Regex("""\d+|[a-z]+""")
}
