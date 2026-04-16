package com.openclaw.tv.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BootstrapAuthRequestDto(
    val phone: String? = null,
    val principalType: String? = null,
    val principalKey: String? = null,
    val principalLabel: String? = null,
    val projectKey: String? = null,
    val deviceFingerprint: String,
    val deviceName: String? = null,
    val osFamily: String? = null,
    val osVersion: String? = null,
    val clientVersion: String? = null,
    val runtimeVersion: String? = null,
    val deviceMetadata: Map<String, String> = emptyMap(),
    val openclawVersion: String? = null,
)

@Serializable
data class BootstrapAuthEnvelope(
    val status: String,
    val user: BootstrapUserDto,
    val device: BootstrapDeviceDto,
    val session: BootstrapSessionDto,
    val upgrade: UpgradeDto,
    @SerialName("modelAccess")
    val modelAccess: ModelAccessDto,
)

@Serializable
data class BootstrapUserDto(
    val id: String,
    val principalType: String,
    val principalKey: String,
    val principalLabel: String,
    val phone: String,
    val source: String,
    val status: String,
)

@Serializable
data class BootstrapDeviceDto(
    val id: String,
)

@Serializable
data class BootstrapSessionDto(
    val token: String,
    val expiresAt: String,
)

@Serializable
data class UpgradeDto(
    val state: String,
    val channel: String,
    val currentVersion: String,
    val minSupportedVersion: String,
    val latestVersion: String,
    val targetVersion: String,
)

@Serializable
data class ModelAccessDto(
    val mode: String,
    val providers: List<String>,
    val defaultModel: String,
    val allowedModels: List<String>,
)
