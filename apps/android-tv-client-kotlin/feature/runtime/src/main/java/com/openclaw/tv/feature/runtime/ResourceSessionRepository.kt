package com.openclaw.tv.feature.runtime

import com.openclaw.tv.core.network.PlatformApi
import com.openclaw.tv.core.network.dto.TvEntitlementSummaryDto
import com.openclaw.tv.core.network.dto.TvResourceAppAccountLeaseDto
import com.openclaw.tv.core.network.dto.TvResourceModelLeaseDto
import com.openclaw.tv.core.network.dto.TvResourceSessionDto
import com.openclaw.tv.core.network.dto.TvResourceSessionReferenceDto
import com.openclaw.tv.core.network.dto.TvResourceSessionRequestDto
import com.openclaw.tv.core.storage.ResourceSessionStore
import com.openclaw.tv.core.storage.SessionStore
import com.openclaw.tv.core.storage.StoredEntitlementSnapshot
import com.openclaw.tv.core.storage.StoredResourceAppAccountLease
import com.openclaw.tv.core.storage.StoredResourceModelLease
import com.openclaw.tv.core.storage.StoredResourceSession
import com.openclaw.tv.core.storage.StoredSession

class ResourceSessionRepository(
    private val platformApi: PlatformApi,
    private val sessionStore: SessionStore,
    private val resourceSessionStore: ResourceSessionStore,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {

    suspend fun getStoredSession(): StoredSession? = sessionStore.read()

    suspend fun getStoredResourceSession(): StoredResourceSession? = resourceSessionStore.read()

    suspend fun request(
        appId: String? = null,
        providerScope: String? = null,
        leaseProfile: String? = null,
    ): StoredResourceSession {
        val session = requireSession()
        val response = platformApi.requestResourceSession(
            sessionToken = session.sessionToken,
            request = TvResourceSessionRequestDto(
                appId = appId?.trim()?.takeIf(String::isNotBlank),
                providerScope = providerScope?.trim()?.takeIf(String::isNotBlank),
                leaseProfile = leaseProfile?.trim()?.takeIf(String::isNotBlank),
            ),
        )
        return persist(response)
    }

    suspend fun poll(): StoredResourceSession {
        val session = requireSession()
        val current = resourceSessionStore.read()
        val response = platformApi.getResourceSessionStatus(
            sessionToken = session.sessionToken,
            resourceSessionId = current?.resourceSessionId?.takeIf(String::isNotBlank),
        )
        return persist(response)
    }

    suspend fun renew(): StoredResourceSession {
        val session = requireSession()
        val current = requireResourceSession()
        val response = platformApi.renewResourceSession(
            sessionToken = session.sessionToken,
            request = TvResourceSessionReferenceDto(
                resourceSessionId = current.resourceSessionId.takeIf(String::isNotBlank),
            ),
        )
        return persist(response)
    }

    suspend fun release(): TvResourceSessionDto {
        val session = requireSession()
        val current = resourceSessionStore.read()
        val response = platformApi.releaseResourceSession(
            sessionToken = session.sessionToken,
            request = TvResourceSessionReferenceDto(
                resourceSessionId = current?.resourceSessionId?.takeIf(String::isNotBlank),
            ),
        )
        resourceSessionStore.clear()
        return response
    }

    suspend fun clearLocalResourceSession() {
        resourceSessionStore.clear()
    }

    private suspend fun persist(response: TvResourceSessionDto): StoredResourceSession {
        val stored = response.toStoredResourceSession(polledAtEpochMs = nowEpochMs())
        resourceSessionStore.save(stored)
        return stored
    }

    private suspend fun requireSession(): StoredSession {
        return sessionStore.read() ?: error("Session has not been bootstrapped")
    }

    private suspend fun requireResourceSession(): StoredResourceSession {
        return resourceSessionStore.read() ?: error("Resource session has not been requested")
    }
}

internal fun TvResourceSessionDto.toStoredResourceSession(
    polledAtEpochMs: Long,
): StoredResourceSession {
    return StoredResourceSession(
        resourceSessionId = resourceSessionId.trim(),
        queueStatus = queueStatus.normalizedQueueStatus(),
        priorityClass = priorityClass.trim(),
        queuePosition = queuePosition,
        estimatedWaitSeconds = estimatedWaitSeconds,
        appAccountLease = appAccountLease?.toStoredResourceAppAccountLease(),
        modelLease = modelLease?.toStoredResourceModelLease(),
        entitlementSummary = entitlementSummary.toStoredEntitlementSnapshot(),
        expiresAt = expiresAt?.trim()?.takeIf(String::isNotBlank),
        updatedAt = updatedAt.trim(),
        polledAtEpochMs = polledAtEpochMs,
    )
}

private fun TvResourceAppAccountLeaseDto.toStoredResourceAppAccountLease(): StoredResourceAppAccountLease {
    return StoredResourceAppAccountLease(
        leaseId = leaseId.trim(),
        appId = appId.trim(),
        accountLabel = accountLabel.trim(),
        expiresAt = expiresAt.trim(),
    )
}

private fun TvResourceModelLeaseDto.toStoredResourceModelLease(): StoredResourceModelLease {
    return StoredResourceModelLease(
        leaseId = leaseId.trim(),
        providerScope = providerScope.trim(),
        leaseMode = leaseMode.trim(),
        leaseProfile = leaseProfile.trim(),
        expiresAt = expiresAt.trim(),
    )
}

private fun TvEntitlementSummaryDto.toStoredEntitlementSnapshot(): StoredEntitlementSnapshot {
    return StoredEntitlementSnapshot(
        accountId = accountId.trim(),
        displayId = displayId.trim(),
        planCode = planCode.trim(),
        paymentState = paymentState.trim().lowercase().ifBlank { "unknown" },
        priorityClass = priorityClass.trim(),
        renewalState = renewalState.trim(),
    )
}

internal fun String.normalizedQueueStatus(): String {
    return trim().lowercase().ifBlank { "not_requested" }
}
