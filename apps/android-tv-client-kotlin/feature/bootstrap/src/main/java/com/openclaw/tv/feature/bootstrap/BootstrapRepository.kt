package com.openclaw.tv.feature.bootstrap

import com.openclaw.tv.core.network.PlatformApi
import com.openclaw.tv.core.network.dto.BootstrapAuthEnvelope
import com.openclaw.tv.core.network.dto.BootstrapAuthRequestDto
import com.openclaw.tv.core.network.dto.ClientPolicyDto
import com.openclaw.tv.core.network.dto.IssueLeaseRequestDto
import com.openclaw.tv.core.network.dto.LeaseDto
import com.openclaw.tv.core.network.dto.ReleaseDto
import com.openclaw.tv.core.network.dto.ReleaseLeaseRequestDto
import com.openclaw.tv.core.network.dto.RenewLeaseRequestDto
import com.openclaw.tv.core.storage.LeaseStore
import com.openclaw.tv.core.storage.SessionStore
import com.openclaw.tv.core.storage.StoredLease
import com.openclaw.tv.core.storage.StoredSession

class BootstrapRepository(
    private val platformApi: PlatformApi,
    private val sessionStore: SessionStore,
    private val leaseStore: LeaseStore,
) {

    suspend fun bootstrapAuth(request: BootstrapAuthRequestDto): BootstrapAuthEnvelope {
        val response = platformApi.bootstrapAuth(request)
        sessionStore.save(
            StoredSession(
                projectKey = request.projectKey.orEmpty(),
                sessionToken = response.session.token,
                expiresAt = response.session.expiresAt,
                userId = response.user.id,
                deviceId = response.device.id,
                principalLabel = response.user.principalLabel,
            ),
        )
        return response
    }

    suspend fun bootstrapSession(request: BootstrapAuthRequestDto): StoredSession {
        bootstrapAuth(request)
        return requireSession()
    }

    suspend fun getStoredSession(): StoredSession? = sessionStore.read()

    suspend fun getStoredLease(): StoredLease? = leaseStore.read()

    suspend fun clearSession() {
        sessionStore.clear()
    }

    suspend fun clearLease() {
        leaseStore.clear()
    }

    suspend fun clearLocalState() {
        leaseStore.clear()
        sessionStore.clear()
    }

    suspend fun fetchPolicy(): ClientPolicyDto {
        val session = requireSession()
        return platformApi.getPolicy(
            sessionToken = session.sessionToken,
            projectKey = session.projectKey.ifBlank { null },
        ).policy
    }

    suspend fun fetchLatestRelease(channel: String? = null): ReleaseDto? {
        val session = requireSession()
        return platformApi.getLatestRelease(
            sessionToken = session.sessionToken,
            channel = channel,
            projectKey = session.projectKey.ifBlank { null },
        ).release
    }

    suspend fun issueLease(
        providerScope: String? = null,
        leaseProfile: String? = null,
    ): StoredLease {
        val session = requireSession()
        val response = platformApi.issueLease(
            sessionToken = session.sessionToken,
            request = IssueLeaseRequestDto(
                projectKey = session.projectKey.ifBlank { null },
                providerScope = providerScope,
                leaseProfile = leaseProfile,
            ),
        )
        val storedLease = response.lease.toStoredLease(response.proxy.baseUrl)
        leaseStore.save(storedLease)
        return storedLease
    }

    suspend fun renewLease(): StoredLease {
        val session = requireSession()
        val lease = requireLease()
        val response = platformApi.renewLease(
            sessionToken = session.sessionToken,
            request = RenewLeaseRequestDto(
                projectKey = session.projectKey.ifBlank { null },
                providerScope = lease.providerScope,
                leaseId = lease.id,
                leaseToken = lease.token,
            ),
        )
        val renewed = response.lease?.toStoredLease(lease.proxyBaseUrl, lease.token)
            ?: error("Lease renew returned empty lease")
        leaseStore.save(renewed)
        return renewed
    }

    suspend fun releaseLease(): Boolean {
        val session = requireSession()
        val lease = leaseStore.read() ?: return false
        val response = platformApi.releaseLease(
            sessionToken = session.sessionToken,
            request = ReleaseLeaseRequestDto(
                projectKey = session.projectKey.ifBlank { null },
                providerScope = lease.providerScope,
                leaseId = lease.id,
                leaseToken = lease.token,
            ),
        )
        if (response.released) {
            leaseStore.clear()
        }
        return response.released
    }

    private suspend fun requireSession(): StoredSession {
        return sessionStore.read() ?: error("Session has not been bootstrapped")
    }

    private suspend fun requireLease(): StoredLease {
        return leaseStore.read() ?: error("Lease has not been issued")
    }
}

internal fun LeaseDto.toStoredLease(
    proxyBaseUrl: String,
    persistedToken: String? = null,
): StoredLease {
    return StoredLease(
        id = id,
        token = token ?: persistedToken.orEmpty(),
        providerScope = providerScope,
        expiresAt = expiresAt,
        leaseMode = leaseMode,
        leaseProfile = leaseProfile,
        lastUsedAt = lastUsedAt,
        lastRenewedAt = lastRenewedAt,
        sticky = sticky,
        proxyBaseUrl = proxyBaseUrl,
    )
}
