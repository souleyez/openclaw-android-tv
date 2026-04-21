package com.openclaw.tv.feature.runtime

import com.openclaw.tv.core.storage.toClientVisibleResourceSession
import com.openclaw.tv.core.storage.StoredResourceSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ResourceSessionRuntimePhase {
    IDLE,
    ACTIVE,
    DEGRADED,
}

data class ResourceSessionRuntimeState(
    val phase: ResourceSessionRuntimePhase = ResourceSessionRuntimePhase.IDLE,
    val queueStatus: String = "not_requested",
    val resourceSession: StoredResourceSession? = null,
    val nextPollAfterSeconds: Int? = null,
    val errorMessage: String? = null,
    val lastSyncedAtEpochMs: Long? = null,
)

class ResourceSessionCoordinator(
    private val repository: ResourceSessionRepository,
    private val pollPolicy: ResourceSessionPollPolicy = ResourceSessionPollPolicy(),
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {

    private val _state = MutableStateFlow(ResourceSessionRuntimeState())
    val state: StateFlow<ResourceSessionRuntimeState> = _state.asStateFlow()

    suspend fun resume() {
        val stored = normalizeForClient(repository.getStoredResourceSession())
        _state.value = stored?.toRuntimeState(
            pollPolicy = pollPolicy,
            nowEpochMs = nowEpochMs(),
        ) ?: ResourceSessionRuntimeState()
    }

    suspend fun request(
        appId: String? = null,
        providerScope: String? = null,
        leaseProfile: String? = null,
    ) {
        runMutatingOperation {
            repository.request(
                appId = appId,
                providerScope = providerScope,
                leaseProfile = leaseProfile,
            )
        }
    }

    suspend fun poll() {
        runMutatingOperation {
            repository.poll()
        }
    }

    suspend fun renew() {
        runMutatingOperation {
            repository.renew()
        }
    }

    suspend fun release() {
        val releasedQueueStatus = try {
            repository.release().queueStatus.normalizedQueueStatus().ifBlank { "released" }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            degrade(error)
            return
        }
        _state.value = ResourceSessionRuntimeState(
            phase = ResourceSessionRuntimePhase.IDLE,
            queueStatus = if (releasedQueueStatus == "not_requested") "released" else releasedQueueStatus,
            resourceSession = null,
            nextPollAfterSeconds = null,
            errorMessage = null,
            lastSyncedAtEpochMs = nowEpochMs(),
        )
    }

    private suspend fun runMutatingOperation(
        operation: suspend () -> StoredResourceSession,
    ) {
        try {
            val stored = normalizeForClient(operation()) ?: error("Resource session operation returned no state")
            _state.value = stored.toRuntimeState(
                pollPolicy = pollPolicy,
                nowEpochMs = nowEpochMs(),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            degrade(error)
        }
    }

    private suspend fun degrade(error: Throwable) {
        val preservedSession = normalizeForClient(
            _state.value.resourceSession ?: repository.getStoredResourceSession(),
        )
        _state.value = ResourceSessionRuntimeState(
            phase = ResourceSessionRuntimePhase.DEGRADED,
            queueStatus = preservedSession?.queueStatus ?: _state.value.queueStatus.normalizedQueueStatus(),
            resourceSession = preservedSession,
            nextPollAfterSeconds = pollPolicy.nextPollAfterSeconds(preservedSession),
            errorMessage = error.message ?: error::class.java.simpleName,
            lastSyncedAtEpochMs = _state.value.lastSyncedAtEpochMs,
        )
    }

    private suspend fun normalizeForClient(
        session: StoredResourceSession?,
    ): StoredResourceSession? {
        session ?: return null
        val normalized = session.toClientVisibleResourceSession(nowEpochMs())
        if (normalized != session) {
            repository.saveLocalResourceSession(normalized)
        }
        return normalized
    }
}

private fun StoredResourceSession.toRuntimeState(
    pollPolicy: ResourceSessionPollPolicy,
    nowEpochMs: Long,
): ResourceSessionRuntimeState {
    val resolvedQueueStatus = queueStatus.normalizedQueueStatus()
    return ResourceSessionRuntimeState(
        phase = if (resolvedQueueStatus in setOf("not_requested", "released", "rejected", "expired")) {
            ResourceSessionRuntimePhase.IDLE
        } else {
            ResourceSessionRuntimePhase.ACTIVE
        },
        queueStatus = resolvedQueueStatus,
        resourceSession = this,
        nextPollAfterSeconds = pollPolicy.nextPollAfterSeconds(this),
        errorMessage = null,
        lastSyncedAtEpochMs = nowEpochMs,
    )
}
