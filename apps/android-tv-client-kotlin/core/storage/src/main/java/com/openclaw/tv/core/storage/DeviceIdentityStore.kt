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
import java.util.UUID

@Serializable
data class StoredDeviceIdentity(
    val installationId: String,
    val principalKey: String,
    val principalLabel: String,
    val createdAt: String,
)

interface DeviceIdentityStore {
    val identity: Flow<StoredDeviceIdentity?>
    suspend fun read(): StoredDeviceIdentity?
    suspend fun save(value: StoredDeviceIdentity)
    suspend fun clear()
    suspend fun ensureIdentity(): StoredDeviceIdentity
}

private val Context.deviceIdentityDataStore by preferencesDataStore(name = "openclaw_device_identity_store")

class DataStoreDeviceIdentityStore(
    private val context: Context,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    },
) : DeviceIdentityStore {

    override val identity: Flow<StoredDeviceIdentity?> = context.deviceIdentityDataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { prefs ->
            prefs[IdentityJsonKey]?.let { encoded ->
                json.decodeFromString(StoredDeviceIdentity.serializer(), encoded)
            }
        }

    override suspend fun read(): StoredDeviceIdentity? = identity.first()

    override suspend fun save(value: StoredDeviceIdentity) {
        context.deviceIdentityDataStore.edit { prefs ->
            prefs[IdentityJsonKey] = json.encodeToString(StoredDeviceIdentity.serializer(), value)
        }
    }

    override suspend fun clear() {
        context.deviceIdentityDataStore.edit { prefs ->
            prefs.remove(IdentityJsonKey)
        }
    }

    override suspend fun ensureIdentity(): StoredDeviceIdentity {
        val existing = read()
        if (existing != null) {
            return existing
        }
        val installationId = UUID.randomUUID().toString()
        val generated = StoredDeviceIdentity(
            installationId = installationId,
            principalKey = "tv-$installationId",
            principalLabel = "OpenClaw TV",
            createdAt = System.currentTimeMillis().toString(),
        )
        save(generated)
        return generated
    }

    private companion object {
        val IdentityJsonKey: Preferences.Key<String> = stringPreferencesKey("device_identity_json")
    }
}

class InMemoryDeviceIdentityStore(initial: StoredDeviceIdentity? = null) : DeviceIdentityStore {
    private val backing = MutableStateFlow(initial)

    override val identity: Flow<StoredDeviceIdentity?> = backing

    override suspend fun read(): StoredDeviceIdentity? = backing.value

    override suspend fun save(value: StoredDeviceIdentity) {
        backing.value = value
    }

    override suspend fun clear() {
        backing.value = null
    }

    override suspend fun ensureIdentity(): StoredDeviceIdentity {
        return backing.value ?: StoredDeviceIdentity(
            installationId = "install-test-01",
            principalKey = "tv-install-test-01",
            principalLabel = "OpenClaw TV",
            createdAt = "2026-04-16T00:00:00Z",
        ).also { generated ->
            backing.value = generated
        }
    }
}
