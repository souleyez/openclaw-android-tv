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
data class StoredLease(
    val id: String,
    val token: String,
    val providerScope: String,
    val expiresAt: String,
    val leaseMode: String,
    val leaseProfile: String,
    val lastUsedAt: String,
    val lastRenewedAt: String,
    val sticky: Boolean,
    val proxyBaseUrl: String,
)

interface LeaseStore {
    val lease: Flow<StoredLease?>
    suspend fun read(): StoredLease?
    suspend fun save(value: StoredLease)
    suspend fun clear()
}

private val Context.leaseDataStore by preferencesDataStore(name = "openclaw_lease_store")

class DataStoreLeaseStore(
    private val context: Context,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    },
) : LeaseStore {

    override val lease: Flow<StoredLease?> = context.leaseDataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { prefs ->
            prefs[LeaseJsonKey]?.let { encoded ->
                json.decodeFromString(StoredLease.serializer(), encoded)
            }
        }

    override suspend fun read(): StoredLease? = lease.first()

    override suspend fun save(value: StoredLease) {
        context.leaseDataStore.edit { prefs ->
            prefs[LeaseJsonKey] = json.encodeToString(StoredLease.serializer(), value)
        }
    }

    override suspend fun clear() {
        context.leaseDataStore.edit { prefs ->
            prefs.remove(LeaseJsonKey)
        }
    }

    private companion object {
        val LeaseJsonKey: Preferences.Key<String> = stringPreferencesKey("lease_json")
    }
}

class InMemoryLeaseStore(initial: StoredLease? = null) : LeaseStore {
    private val backing = MutableStateFlow(initial)

    override val lease: Flow<StoredLease?> = backing

    override suspend fun read(): StoredLease? = backing.value

    override suspend fun save(value: StoredLease) {
        backing.value = value
    }

    override suspend fun clear() {
        backing.value = null
    }
}
