package com.openclaw.tv.core.storage

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException

@Serializable
data class StoredUpgradeState(
    val lastSeenVersion: String? = null,
    val pendingInstallVersion: String? = null,
    val pendingInstallDownloadId: Long? = null,
    val pendingSuccessVersion: String? = null,
    val updatedAtEpochMs: Long = 0L,
)

interface UpgradeStateStore {
    val upgradeState: Flow<StoredUpgradeState?>
    suspend fun read(): StoredUpgradeState?
    suspend fun save(value: StoredUpgradeState)
    suspend fun clear()
    suspend fun recordLaunch(currentVersion: String): StoredUpgradeState
    suspend fun markInstallPromptOpened(targetVersion: String?, downloadId: Long?)
    suspend fun clearPendingInstall()
    suspend fun consumeSuccessVersion(): String?
}

private val Context.upgradeStateDataStore by preferencesDataStore(name = "openclaw_upgrade_state_store")

class DataStoreUpgradeStateStore(
    private val context: Context,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    },
) : UpgradeStateStore {

    override val upgradeState: Flow<StoredUpgradeState?> = context.upgradeStateDataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { prefs ->
            prefs[UpgradeStateJsonKey]?.let { encoded ->
                json.decodeFromString(StoredUpgradeState.serializer(), encoded)
            }
        }

    override suspend fun read(): StoredUpgradeState? = upgradeState.first()

    override suspend fun save(value: StoredUpgradeState) {
        context.upgradeStateDataStore.edit { prefs ->
            prefs[UpgradeStateJsonKey] = json.encodeToString(StoredUpgradeState.serializer(), value)
        }
    }

    override suspend fun clear() {
        context.upgradeStateDataStore.edit { prefs ->
            prefs.remove(UpgradeStateJsonKey)
        }
    }

    override suspend fun recordLaunch(currentVersion: String): StoredUpgradeState {
        val existing = read()
        val now = nowEpochMs()
        val next = when {
            existing == null -> StoredUpgradeState(
                lastSeenVersion = currentVersion,
                updatedAtEpochMs = now,
            )

            existing.lastSeenVersion == currentVersion -> existing.copy(
                updatedAtEpochMs = now,
            )

            else -> existing.copy(
                lastSeenVersion = currentVersion,
                pendingInstallVersion = null,
                pendingInstallDownloadId = null,
                pendingSuccessVersion = currentVersion,
                updatedAtEpochMs = now,
            )
        }
        save(next)
        return next
    }

    override suspend fun markInstallPromptOpened(targetVersion: String?, downloadId: Long?) {
        val existing = read() ?: StoredUpgradeState()
        save(
            existing.copy(
                pendingInstallVersion = targetVersion?.trim()?.takeIf(String::isNotBlank),
                pendingInstallDownloadId = downloadId?.takeIf { it > 0L },
                updatedAtEpochMs = nowEpochMs(),
            ),
        )
    }

    override suspend fun clearPendingInstall() {
        val existing = read() ?: return
        save(
            existing.copy(
                pendingInstallVersion = null,
                pendingInstallDownloadId = null,
                updatedAtEpochMs = nowEpochMs(),
            ),
        )
    }

    override suspend fun consumeSuccessVersion(): String? {
        val existing = read() ?: return null
        val successVersion = existing.pendingSuccessVersion?.takeIf(String::isNotBlank) ?: return null
        save(
            existing.copy(
                pendingSuccessVersion = null,
                updatedAtEpochMs = nowEpochMs(),
            ),
        )
        return successVersion
    }

    private companion object {
        val UpgradeStateJsonKey: Preferences.Key<String> = stringPreferencesKey("upgrade_state_json")
    }
}

class InMemoryUpgradeStateStore(
    initial: StoredUpgradeState? = null,
    private val nowEpochMs: () -> Long = { 0L },
) : UpgradeStateStore {
    private val backing = MutableStateFlow(initial)

    override val upgradeState: Flow<StoredUpgradeState?> = backing

    override suspend fun read(): StoredUpgradeState? = backing.value

    override suspend fun save(value: StoredUpgradeState) {
        backing.value = value
    }

    override suspend fun clear() {
        backing.value = null
    }

    override suspend fun recordLaunch(currentVersion: String): StoredUpgradeState {
        val existing = read()
        val now = nowEpochMs()
        val next = when {
            existing == null -> StoredUpgradeState(
                lastSeenVersion = currentVersion,
                updatedAtEpochMs = now,
            )

            existing.lastSeenVersion == currentVersion -> existing.copy(
                updatedAtEpochMs = now,
            )

            else -> existing.copy(
                lastSeenVersion = currentVersion,
                pendingInstallVersion = null,
                pendingInstallDownloadId = null,
                pendingSuccessVersion = currentVersion,
                updatedAtEpochMs = now,
            )
        }
        backing.value = next
        return next
    }

    override suspend fun markInstallPromptOpened(targetVersion: String?, downloadId: Long?) {
        val existing = read() ?: StoredUpgradeState()
        backing.value = existing.copy(
            pendingInstallVersion = targetVersion?.trim()?.takeIf(String::isNotBlank),
            pendingInstallDownloadId = downloadId?.takeIf { it > 0L },
            updatedAtEpochMs = nowEpochMs(),
        )
    }

    override suspend fun clearPendingInstall() {
        val existing = read() ?: return
        backing.value = existing.copy(
            pendingInstallVersion = null,
            pendingInstallDownloadId = null,
            updatedAtEpochMs = nowEpochMs(),
        )
    }

    override suspend fun consumeSuccessVersion(): String? {
        val existing = read() ?: return null
        val successVersion = existing.pendingSuccessVersion?.takeIf(String::isNotBlank) ?: return null
        backing.value = existing.copy(
            pendingSuccessVersion = null,
            updatedAtEpochMs = nowEpochMs(),
        )
        return successVersion
    }
}
