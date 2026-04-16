package com.openclaw.tv.feature.bootstrap

import com.openclaw.tv.core.network.dto.BootstrapAuthRequestDto
import com.openclaw.tv.core.network.dto.ClientPolicyDto
import com.openclaw.tv.core.network.dto.ReleaseDto
import com.openclaw.tv.core.storage.StoredLease
import com.openclaw.tv.core.storage.StoredSession

data class BootstrapFlowResult(
    val session: StoredSession,
    val policy: ClientPolicyDto,
    val lease: StoredLease,
    val release: ReleaseDto?,
)

class LeaseCoordinator(
    private val repository: BootstrapRepository,
) {

    suspend fun bootstrap(
        request: BootstrapAuthRequestDto,
        leaseProfile: String? = null,
    ): BootstrapFlowResult {
        repository.bootstrapAuth(request)
        val policy = repository.fetchPolicy()
        val lease = repository.issueLease(
            providerScope = policy.providerScopes.firstOrNull(),
            leaseProfile = leaseProfile,
        )
        val release = repository.fetchLatestRelease(policy.channel)
        val session = repository.getStoredSession() ?: error("Stored session missing after bootstrap")
        return BootstrapFlowResult(
            session = session,
            policy = policy,
            lease = lease,
            release = release,
        )
    }

    suspend fun renewActiveLease(): StoredLease = repository.renewLease()

    suspend fun releaseActiveLease(): Boolean = repository.releaseLease()
}
