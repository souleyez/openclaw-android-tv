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
data class StoredEntitlementSummary(
    val accountId: String,
    val displayId: String,
    val planCode: String,
    val paymentState: String,
    val priorityClass: String,
    val renewalState: String,
    val cachedAtEpochMs: Long,
)

interface EntitlementStore {
    val entitlement: Flow<StoredEntitlementSummary?>
    suspend fun read(): StoredEntitlementSummary?
    suspend fun save(value: StoredEntitlementSummary)
    suspend fun clear()
}

private val Context.entitlementDataStore by preferencesDataStore(name = "openclaw_entitlement_store")

class DataStoreEntitlementStore(
    private val context: Context,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    },
) : EntitlementStore {

    override val entitlement: Flow<StoredEntitlementSummary?> = context.entitlementDataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { prefs ->
            prefs[EntitlementJsonKey]?.let { encoded ->
                json.decodeFromString(StoredEntitlementSummary.serializer(), encoded)
            }
        }

    override suspend fun read(): StoredEntitlementSummary? = entitlement.first()

    override suspend fun save(value: StoredEntitlementSummary) {
        context.entitlementDataStore.edit { prefs ->
            prefs[EntitlementJsonKey] = json.encodeToString(StoredEntitlementSummary.serializer(), value)
        }
    }

    override suspend fun clear() {
        context.entitlementDataStore.edit { prefs ->
            prefs.remove(EntitlementJsonKey)
        }
    }

    private companion object {
        val EntitlementJsonKey: Preferences.Key<String> = stringPreferencesKey("entitlement_json")
    }
}

class InMemoryEntitlementStore(initial: StoredEntitlementSummary? = null) : EntitlementStore {
    private val backing = MutableStateFlow(initial)

    override val entitlement: Flow<StoredEntitlementSummary?> = backing

    override suspend fun read(): StoredEntitlementSummary? = backing.value

    override suspend fun save(value: StoredEntitlementSummary) {
        backing.value = value
    }

    override suspend fun clear() {
        backing.value = null
    }
}
