package com.openclaw.tv.feature.bootstrap

import com.openclaw.tv.core.network.PlatformApiException
import com.openclaw.tv.core.network.dto.BootstrapAuthRequestDto
import com.openclaw.tv.core.network.dto.ClientPolicyDto
import com.openclaw.tv.core.network.dto.ReleaseDto
import com.openclaw.tv.core.storage.StoredLease
import com.openclaw.tv.core.storage.StoredSession
import kotlinx.coroutines.CancellationException
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
    private val legacyLeaseCompatibilityEnabled: Boolean = true,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
    private val logWarning: (String, Throwable) -> Unit = { _, _ -> },
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

            logWarning("Bootstrap runtime refresh degraded: ${error.message}", error)
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
            val result = if (legacyLeaseCompatibilityEnabled) {
                leaseCoordinator.bootstrap(
                    request = requestFactory(),
                    leaseProfile = leaseProfile,
                ).toBootstrapSnapshot()
            } else {
                bootstrapWithoutLegacyLease()
            }
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
            ).also {
                logWarning("Bootstrap runtime fresh sync failed: ${error.message}", error)
            }
        }
    }

    private suspend fun refreshExistingSession(session: StoredSession): BootstrapRuntimeState {
        val policy = bootstrapStep("client/policy") {
            repository.fetchPolicy()
        }
        val lease = if (legacyLeaseCompatibilityEnabled) {
            restoreLease(policy)
        } else {
            repository.getStoredLease()
        }
        val release = bootstrapStep("client/releases/latest") {
            repository.fetchLatestRelease(policy.channel)
        }
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

    private suspend fun bootstrapWithoutLegacyLease(): BootstrapSnapshot {
        val request = requestFactory()
        val session = bootstrapStep("client/bootstrap/auth") {
            repository.bootstrapSession(request)
        }
        val policy = bootstrapStep("client/policy") {
            repository.fetchPolicy()
        }
        val release = bootstrapStep("client/releases/latest") {
            repository.fetchLatestRelease(policy.channel)
        }
        return BootstrapSnapshot(
            session = session,
            policy = policy,
            lease = repository.getStoredLease(),
            release = release,
        )
    }

    private suspend fun restoreLease(policy: ClientPolicyDto): StoredLease {
        val existingLease = repository.getStoredLease()
        if (existingLease == null) {
            return issueLease(policy)
        }

        return try {
            bootstrapStep("client/model-lease/renew") {
                repository.renewLease()
            }
        } catch (error: Exception) {
            if (error.isAuthFailure()) {
                throw error
            }
            issueLease(policy)
        }
    }

    private suspend fun issueLease(policy: ClientPolicyDto): StoredLease {
        return bootstrapStep("client/model-lease") {
            repository.issueLease(
                providerScope = policy.providerScopes.firstOrNull(),
                leaseProfile = leaseProfile,
            )
        }
    }

    private suspend fun <T> bootstrapStep(
        label: String,
        block: suspend () -> T,
    ): T {
        return try {
            block()
        } catch (error: Exception) {
            if (error is CancellationException) {
                throw error
            }
            if (error is BootstrapRuntimeStepException) {
                throw error
            }
            throw BootstrapRuntimeStepException(label, error)
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

private data class BootstrapSnapshot(
    val session: StoredSession,
    val policy: ClientPolicyDto,
    val lease: StoredLease?,
    val release: ReleaseDto?,
)

private fun BootstrapFlowResult.toBootstrapSnapshot(): BootstrapSnapshot {
    return BootstrapSnapshot(
        session = session,
        policy = policy,
        lease = lease,
        release = release,
    )
}

private fun Throwable.isAuthFailure(): Boolean {
    val root = unwrapBootstrapStep()
    return root is PlatformApiException && (root.statusCode == 401 || root.statusCode == 403)
}

private fun Throwable.isRuntimeUnavailable(): Boolean {
    val root = unwrapBootstrapStep()
    return root is PlatformApiException && root.statusCode == 404
}

private class BootstrapRuntimeStepException(
    val stepLabel: String,
    cause: Throwable,
) : RuntimeException(
    "$stepLabel failed: ${cause.message ?: cause::class.java.simpleName}",
    cause,
)

private fun Throwable.unwrapBootstrapStep(): Throwable {
    var current = this
    while (current is BootstrapRuntimeStepException && current.cause != null) {
        current = current.cause!!
    }
    return current
}
