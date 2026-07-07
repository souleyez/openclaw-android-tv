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
data class StoredTvHomeConfig(
    val projectKey: String,
    val projectLabel: String,
    val customer: StoredTvHomeCustomer? = null,
    val distribution: StoredTvHomeDistribution? = null,
    val branding: StoredTvHomeBranding? = null,
    val theme: StoredTvHomeTheme? = null,
    val homeApps: List<StoredTvHomeApp> = emptyList(),
    val hotelServices: List<StoredTvHotelService> = emptyList(),
    val runtimeManifestPath: String,
    val entitlementPath: String,
    val resourceSessionBasePath: String,
    val manifestPollAfterSeconds: Int,
    val resourceSessionPollAfterSeconds: Int,
    val backgroundDownloadEnabled: Boolean,
    val idleDownloadOnly: Boolean,
    val cachedAtEpochMs: Long,
)

@Serializable
data class StoredTvHomeCustomer(
    val id: String,
    val slug: String,
    val displayName: String,
    val hotelName: String,
)

@Serializable
data class StoredTvHomeDistribution(
    val distributionKey: String,
    val packageName: String,
    val releaseChannel: String,
)

@Serializable
data class StoredTvHomeBranding(
    val logoUrl: String,
    val intro: String,
    val versionLabel: String,
)

@Serializable
data class StoredTvHomeTheme(
    val defaultMode: String,
    val switcherEnabled: Boolean,
    val dayPalette: Map<String, String>,
    val nightPalette: Map<String, String>,
)

@Serializable
data class StoredTvHomeApp(
    val appId: String,
    val title: String,
    val packageName: String,
    val sortOrder: Int,
)

@Serializable
data class StoredTvHotelService(
    val id: String,
    val title: String,
    val summary: String,
    val imageUrl: String,
    val actionType: String,
    val actionValue: String,
    val sortOrder: Int,
)

interface TvHomeConfigStore {
    val config: Flow<StoredTvHomeConfig?>
    suspend fun read(): StoredTvHomeConfig?
    suspend fun save(value: StoredTvHomeConfig)
    suspend fun clear()
}

private val Context.tvHomeConfigDataStore by preferencesDataStore(name = "openclaw_tv_home_config_store")

class DataStoreTvHomeConfigStore(
    private val context: Context,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    },
) : TvHomeConfigStore {

    override val config: Flow<StoredTvHomeConfig?> = context.tvHomeConfigDataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { prefs ->
            prefs[ConfigJsonKey]?.let { encoded ->
                json.decodeFromString(StoredTvHomeConfig.serializer(), encoded)
            }
        }

    override suspend fun read(): StoredTvHomeConfig? = config.first()

    override suspend fun save(value: StoredTvHomeConfig) {
        context.tvHomeConfigDataStore.edit { prefs ->
            prefs[ConfigJsonKey] = json.encodeToString(StoredTvHomeConfig.serializer(), value)
        }
    }

    override suspend fun clear() {
        context.tvHomeConfigDataStore.edit { prefs ->
            prefs.remove(ConfigJsonKey)
        }
    }

    private companion object {
        val ConfigJsonKey: Preferences.Key<String> = stringPreferencesKey("tv_home_config_json")
    }
}

class InMemoryTvHomeConfigStore(initial: StoredTvHomeConfig? = null) : TvHomeConfigStore {
    private val backing = MutableStateFlow(initial)

    override val config: Flow<StoredTvHomeConfig?> = backing

    override suspend fun read(): StoredTvHomeConfig? = backing.value

    override suspend fun save(value: StoredTvHomeConfig) {
        backing.value = value
    }

    override suspend fun clear() {
        backing.value = null
    }
}
