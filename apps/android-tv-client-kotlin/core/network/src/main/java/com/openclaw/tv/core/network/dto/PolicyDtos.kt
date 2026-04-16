package com.openclaw.tv.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class PolicyEnvelope(
    val status: String,
    val policy: ClientPolicyDto,
)

@Serializable
data class ClientPolicyDto(
    val channel: String,
    val minSupportedVersion: String,
    val targetVersion: String,
    val forceUpgrade: Boolean,
    val allowSelfRegister: Boolean,
    val modelAccessMode: String,
    val providerScopes: List<String>,
    val defaultModel: String,
    val allowedModels: List<String>,
)

@Serializable
data class LatestReleaseEnvelope(
    val status: String,
    val release: ReleaseDto? = null,
)

@Serializable
data class ReleaseDto(
    val id: String,
    val projectKey: String,
    val channel: String,
    val version: String,
    val status: String,
    val artifactType: String,
    val artifactUrl: String,
    val artifactSha256: String,
    val artifactSize: Long,
    val runtimeVersion: String,
    val releaseMetadata: Map<String, String> = emptyMap(),
    val openclawVersion: String,
    val installerVersion: String,
    val minSupportedVersion: String,
    val releaseNotes: String,
    val publishedAt: String,
    val createdAt: String,
    val updatedAt: String,
)
