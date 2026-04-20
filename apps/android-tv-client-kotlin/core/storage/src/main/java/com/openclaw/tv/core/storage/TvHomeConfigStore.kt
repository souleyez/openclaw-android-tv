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
    val countryCode: String,
    val regionCode: String?,
    val backgroundImageUrl: String?,
    val featuredAppIds: List<String>,
    val cachedAtEpochMs: Long,
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
