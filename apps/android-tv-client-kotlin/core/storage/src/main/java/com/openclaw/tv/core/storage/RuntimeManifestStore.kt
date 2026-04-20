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
data class StoredRuntimeManifest(
    val manifestVersion: String,
    val countryCode: String,
    val regionCode: String?,
    val adSlots: List<StoredRuntimeAdSlot>,
    val cachedAtEpochMs: Long,
)

@Serializable
data class StoredRuntimeAdSlot(
    val slotId: String,
    val enabled: Boolean,
    val creatives: List<StoredRuntimeAdCreative>,
)

@Serializable
data class StoredRuntimeAdCreative(
    val creativeId: String,
    val mediaType: String,
    val assetUrl: String,
    val altText: String,
    val clickActionType: String,
    val clickActionValue: String? = null,
    val startsAt: String? = null,
    val endsAt: String? = null,
)

interface RuntimeManifestStore {
    val manifest: Flow<StoredRuntimeManifest?>
    suspend fun read(): StoredRuntimeManifest?
    suspend fun save(value: StoredRuntimeManifest)
    suspend fun clear()
}

private val Context.runtimeManifestDataStore by preferencesDataStore(name = "openclaw_runtime_manifest_store")

class DataStoreRuntimeManifestStore(
    private val context: Context,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    },
) : RuntimeManifestStore {

    override val manifest: Flow<StoredRuntimeManifest?> = context.runtimeManifestDataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { prefs ->
            prefs[ManifestJsonKey]?.let { encoded ->
                json.decodeFromString(StoredRuntimeManifest.serializer(), encoded)
            }
        }

    override suspend fun read(): StoredRuntimeManifest? = manifest.first()

    override suspend fun save(value: StoredRuntimeManifest) {
        context.runtimeManifestDataStore.edit { prefs ->
            prefs[ManifestJsonKey] = json.encodeToString(StoredRuntimeManifest.serializer(), value)
        }
    }

    override suspend fun clear() {
        context.runtimeManifestDataStore.edit { prefs ->
            prefs.remove(ManifestJsonKey)
        }
    }

    private companion object {
        val ManifestJsonKey: Preferences.Key<String> = stringPreferencesKey("runtime_manifest_json")
    }
}

class InMemoryRuntimeManifestStore(initial: StoredRuntimeManifest? = null) : RuntimeManifestStore {
    private val backing = MutableStateFlow(initial)

    override val manifest: Flow<StoredRuntimeManifest?> = backing

    override suspend fun read(): StoredRuntimeManifest? = backing.value

    override suspend fun save(value: StoredRuntimeManifest) {
        backing.value = value
    }

    override suspend fun clear() {
        backing.value = null
    }
}
