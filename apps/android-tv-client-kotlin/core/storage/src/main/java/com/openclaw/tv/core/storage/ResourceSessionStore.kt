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
data class StoredResourceSession(
    val resourceSessionId: String,
    val queueStatus: String,
    val priorityClass: String,
    val queuePosition: Int? = null,
    val estimatedWaitSeconds: Int? = null,
    val appAccountLease: StoredResourceAppAccountLease? = null,
    val modelLease: StoredResourceModelLease? = null,
    val entitlementSummary: StoredEntitlementSnapshot,
    val expiresAt: String? = null,
    val updatedAt: String,
    val polledAtEpochMs: Long,
)

@Serializable
data class StoredResourceAppAccountLease(
    val leaseId: String,
    val appId: String,
    val accountLabel: String,
    val expiresAt: String,
)

@Serializable
data class StoredResourceModelLease(
    val leaseId: String,
    val providerScope: String,
    val leaseMode: String,
    val leaseProfile: String,
    val expiresAt: String,
)

@Serializable
data class StoredEntitlementSnapshot(
    val accountId: String,
    val displayId: String,
    val planCode: String,
    val paymentState: String,
    val priorityClass: String,
    val renewalState: String,
)

interface ResourceSessionStore {
    val resourceSession: Flow<StoredResourceSession?>
    suspend fun read(): StoredResourceSession?
    suspend fun save(value: StoredResourceSession)
    suspend fun clear()
}

private val Context.resourceSessionDataStore by preferencesDataStore(name = "openclaw_resource_session_store")

class DataStoreResourceSessionStore(
    private val context: Context,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    },
) : ResourceSessionStore {

    override val resourceSession: Flow<StoredResourceSession?> = context.resourceSessionDataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { prefs ->
            prefs[ResourceSessionJsonKey]?.let { encoded ->
                json.decodeFromString(StoredResourceSession.serializer(), encoded)
            }
        }

    override suspend fun read(): StoredResourceSession? = resourceSession.first()

    override suspend fun save(value: StoredResourceSession) {
        context.resourceSessionDataStore.edit { prefs ->
            prefs[ResourceSessionJsonKey] = json.encodeToString(StoredResourceSession.serializer(), value)
        }
    }

    override suspend fun clear() {
        context.resourceSessionDataStore.edit { prefs ->
            prefs.remove(ResourceSessionJsonKey)
        }
    }

    private companion object {
        val ResourceSessionJsonKey: Preferences.Key<String> = stringPreferencesKey("resource_session_json")
    }
}

class InMemoryResourceSessionStore(initial: StoredResourceSession? = null) : ResourceSessionStore {
    private val backing = MutableStateFlow(initial)

    override val resourceSession: Flow<StoredResourceSession?> = backing

    override suspend fun read(): StoredResourceSession? = backing.value

    override suspend fun save(value: StoredResourceSession) {
        backing.value = value
    }

    override suspend fun clear() {
        backing.value = null
    }
}
