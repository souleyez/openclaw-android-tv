package com.openclaw.tv.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class IssueLeaseRequestDto(
    val projectKey: String? = null,
    val providerScope: String? = null,
    val leaseProfile: String? = null,
)

@Serializable
data class LeaseStatusRequestDto(
    val projectKey: String? = null,
    val providerScope: String? = null,
    val leaseId: String? = null,
)

@Serializable
data class RenewLeaseRequestDto(
    val projectKey: String? = null,
    val providerScope: String? = null,
    val leaseId: String? = null,
    val leaseToken: String? = null,
)

@Serializable
data class ReleaseLeaseRequestDto(
    val projectKey: String? = null,
    val providerScope: String? = null,
    val leaseId: String? = null,
    val leaseToken: String? = null,
)

@Serializable
data class LeaseEnvelope(
    val status: String,
    val lease: LeaseDto,
    val proxy: ProxyDto,
)

@Serializable
data class LeaseStatusEnvelope(
    val status: String,
    val lease: LeaseDto? = null,
    val availableProfiles: List<LeaseProfileDto> = emptyList(),
    val proxy: ProxyDto = ProxyDto(),
)

@Serializable
data class ReleaseLeaseEnvelope(
    val status: String,
    val released: Boolean,
    val leaseId: String = "",
)

@Serializable
data class LeaseDto(
    val id: String,
    val token: String? = null,
    val expiresAt: String,
    val providerScope: String,
    val leaseMode: String,
    val leaseProfile: String,
    val lastUsedAt: String,
    val lastRenewedAt: String,
    val sticky: Boolean,
)

@Serializable
data class LeaseProfileDto(
    val id: String,
    val ttlMinutes: Int,
    val leaseMode: String,
    val renewWindowSeconds: Int,
    val contentionPriority: Int,
    val contentionIdleReleaseMinutes: Int,
    val sticky: Boolean,
)

@Serializable
data class ProxyDto(
    val baseUrl: String = "",
)
