package com.openclaw.tv.feature.bootstrap

import com.openclaw.tv.core.network.PlatformApiException
import com.openclaw.tv.core.network.dto.BootstrapAuthRequestDto
import com.openclaw.tv.core.network.dto.ClientPolicyDto
import com.openclaw.tv.core.network.dto.ReleaseDto
import com.openclaw.tv.core.storage.StoredLease
import com.openclaw.tv.core.storage.StoredSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class BootstrapRuntimePhase {
    IDLE,
    SYNCING,
    READY,
    DEGRADED,
    FAILED,
}

data class BootstrapRuntimeState(
    val phase: BootstrapRuntimePhase = BootstrapRuntimePhase.IDLE,
    val session: StoredSession? = null,
    val policy: ClientPolicyDto? = null,
    val lease: StoredLease? = null,
    val release: ReleaseDto? = null,
    val upgradeStatus: RuntimeUpgradeStatus? = null,
    val errorMessage: String? = null,
    val lastSyncedAtEpochMs: Long? = null,
)

class BootstrapRuntime(
    private val scope: CoroutineScope,
    private val repository: BootstrapRepository,
    private val leaseCoordinator: LeaseCoordinator,
    private val requestFactory: suspend () -> BootstrapAuthRequestDto,
    private val currentClientVersion: String,
    private val leaseProfile: String? = null,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {

    private val _state = MutableStateFlow(BootstrapRuntimeState())
    val state: StateFlow<BootstrapRuntimeState> = _state.asStateFlow()

    private var syncJob: Job? = null

    fun start() {
        launchSyncIfIdle()
    }

    fun refresh() {
        launchSyncIfIdle()
    }

    suspend fun syncNow() {
        val previousState = _state.value
        _state.value = previousState.copy(
            phase = BootstrapRuntimePhase.SYNCING,
            errorMessage = null,
        )

        val storedSession = repository.getStoredSession()
        val storedLease = repository.getStoredLease()

        if (storedSession == null) {
            _state.value = bootstrapFresh()
            return
        }

        try {
            _state.value = refreshExistingSession(storedSession)
        } catch (error: Exception) {
            if (error.isRuntimeUnavailable()) {
                repository.clearLocalState()
                _state.value = BootstrapRuntimeState(
                    phase = BootstrapRuntimePhase.IDLE,
                    lastSyncedAtEpochMs = previousState.lastSyncedAtEpochMs,
                )
                return
            }

            if (error.isAuthFailure()) {
                repository.clearLocalState()
                _state.value = bootstrapFresh()
                return
            }

            _state.value = BootstrapRuntimeState(
                phase = BootstrapRuntimePhase.DEGRADED,
                session = storedSession,
                policy = previousState.policy,
                lease = storedLease,
                release = previousState.release,
                upgradeStatus = previousState.upgradeStatus,
                errorMessage = error.message ?: error::class.java.simpleName,
                lastSyncedAtEpochMs = previousState.lastSyncedAtEpochMs,
            )
        }
    }

    private suspend fun bootstrapFresh(): BootstrapRuntimeState {
        return try {
            val result = leaseCoordinator.bootstrap(
                request = requestFactory(),
                leaseProfile = leaseProfile,
            )
            BootstrapRuntimeState(
                phase = BootstrapRuntimePhase.READY,
                session = result.session,
                policy = result.policy,
                lease = result.lease,
                release = result.release,
                upgradeStatus = RuntimeUpgradeStatusResolver.resolve(
                    currentClientVersion = currentClientVersion,
                    policy = result.policy,
                    release = result.release,
                ),
                lastSyncedAtEpochMs = nowEpochMs(),
            )
        } catch (error: Exception) {
            if (error.isRuntimeUnavailable()) {
                repository.clearLocalState()
                return BootstrapRuntimeState(
                    phase = BootstrapRuntimePhase.IDLE,
                    lastSyncedAtEpochMs = _state.value.lastSyncedAtEpochMs,
                )
            }

            BootstrapRuntimeState(
                phase = BootstrapRuntimePhase.FAILED,
                errorMessage = error.message ?: error::class.java.simpleName,
                lastSyncedAtEpochMs = _state.value.lastSyncedAtEpochMs,
            )
        }
    }

    private suspend fun refreshExistingSession(session: StoredSession): BootstrapRuntimeState {
        val policy = repository.fetchPolicy()
        val lease = restoreLease(policy)
        val release = repository.fetchLatestRelease(policy.channel)
        val resolvedSession = repository.getStoredSession() ?: session

        return BootstrapRuntimeState(
            phase = BootstrapRuntimePhase.READY,
            session = resolvedSession,
            policy = policy,
            lease = lease,
            release = release,
            upgradeStatus = RuntimeUpgradeStatusResolver.resolve(
                currentClientVersion = currentClientVersion,
                policy = policy,
                release = release,
            ),
            lastSyncedAtEpochMs = nowEpochMs(),
        )
    }

    private suspend fun restoreLease(policy: ClientPolicyDto): StoredLease {
        val existingLease = repository.getStoredLease()
        if (existingLease == null) {
            return repository.issueLease(
                providerScope = policy.providerScopes.firstOrNull(),
                leaseProfile = leaseProfile,
            )
        }

        return try {
            repository.renewLease()
        } catch (error: Exception) {
            if (error.isAuthFailure()) {
                throw error
            }
            repository.issueLease(
                providerScope = policy.providerScopes.firstOrNull(),
                leaseProfile = leaseProfile,
            )
        }
    }

    private fun launchSyncIfIdle() {
        if (syncJob?.isActive == true) {
            return
        }
        syncJob = scope.launch {
            try {
                syncNow()
            } finally {
                syncJob = null
            }
        }
    }
}

private fun Throwable.isAuthFailure(): Boolean {
    return this is PlatformApiException && (statusCode == 401 || statusCode == 403)
}

private fun Throwable.isRuntimeUnavailable(): Boolean {
    return this is PlatformApiException && statusCode == 404
}
