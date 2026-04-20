package com.openclaw.tv.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class TvHomeConfigDto(
    val projectKey: String = "openclaw-android-tv",
    val projectLabel: String = "OpenClaw Android TV",
    val runtimeManifestPath: String = "/api/me/runtime-manifest",
    val entitlementPath: String = "/api/me/entitlement",
    val resourceSessionBasePath: String = "/api/client/resource-session",
    val manifestPollAfterSeconds: Int = 900,
    val resourceSessionPollAfterSeconds: Int = 15,
    val backgroundDownloadEnabled: Boolean = true,
    val idleDownloadOnly: Boolean = true,
)
