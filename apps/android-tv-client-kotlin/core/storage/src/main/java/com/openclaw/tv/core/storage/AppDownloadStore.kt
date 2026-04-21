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
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.IOException

@Serializable
data class StoredAppDownloadState(
    val appId: String,
    val title: String,
    val packageName: String,
    val versionCode: Long,
    val versionName: String,
    val downloadUrl: String,
    val sha256: String,
    val status: String,
    val downloadId: Long? = null,
    val localFilePath: String? = null,
    val downloadedBytes: Long? = null,
    val totalBytes: Long? = null,
    val downloadDetailMessage: String? = null,
    val errorMessage: String? = null,
    val updatedAtEpochMs: Long,
)

interface AppDownloadStore {
    val downloads: Flow<Map<String, StoredAppDownloadState>>
    suspend fun readAll(): Map<String, StoredAppDownloadState>
    suspend fun read(appId: String): StoredAppDownloadState?
    suspend fun upsert(value: StoredAppDownloadState)
    suspend fun remove(appId: String)
    suspend fun clear()
}

private val Context.appDownloadDataStore by preferencesDataStore(name = "openclaw_app_download_store")

class DataStoreAppDownloadStore(
    private val context: Context,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    },
) : AppDownloadStore {

    override val downloads: Flow<Map<String, StoredAppDownloadState>> = context.appDownloadDataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { prefs ->
            prefs[DownloadsJsonKey]?.let { encoded ->
                json.decodeFromString(DownloadMapSerializer, encoded)
            } ?: emptyMap()
        }

    override suspend fun readAll(): Map<String, StoredAppDownloadState> = downloads.first()

    override suspend fun read(appId: String): StoredAppDownloadState? = readAll()[appId]

    override suspend fun upsert(value: StoredAppDownloadState) {
        context.appDownloadDataStore.edit { prefs ->
            val next = decodeDownloads(prefs).toMutableMap().apply {
                this[value.appId] = value
            }
            prefs[DownloadsJsonKey] = json.encodeToString(DownloadMapSerializer, next)
        }
    }

    override suspend fun remove(appId: String) {
        context.appDownloadDataStore.edit { prefs ->
            val next = decodeDownloads(prefs).toMutableMap().apply {
                remove(appId)
            }
            if (next.isEmpty()) {
                prefs.remove(DownloadsJsonKey)
            } else {
                prefs[DownloadsJsonKey] = json.encodeToString(DownloadMapSerializer, next)
            }
        }
    }

    override suspend fun clear() {
        context.appDownloadDataStore.edit { prefs ->
            prefs.remove(DownloadsJsonKey)
        }
    }

    private companion object {
        val DownloadsJsonKey: Preferences.Key<String> = stringPreferencesKey("app_downloads_json")
        val DownloadMapSerializer = MapSerializer(String.serializer(), StoredAppDownloadState.serializer())
    }

    private fun decodeDownloads(prefs: Preferences): Map<String, StoredAppDownloadState> {
        return prefs[DownloadsJsonKey]?.let { encoded ->
            json.decodeFromString(DownloadMapSerializer, encoded)
        } ?: emptyMap()
    }
}

class InMemoryAppDownloadStore(
    initial: Map<String, StoredAppDownloadState> = emptyMap(),
) : AppDownloadStore {
    private val backing = MutableStateFlow(initial)

    override val downloads: Flow<Map<String, StoredAppDownloadState>> = backing

    override suspend fun readAll(): Map<String, StoredAppDownloadState> = backing.value

    override suspend fun read(appId: String): StoredAppDownloadState? = backing.value[appId]

    override suspend fun upsert(value: StoredAppDownloadState) {
        backing.value = backing.value.toMutableMap().apply {
            this[value.appId] = value
        }
    }

    override suspend fun remove(appId: String) {
        backing.value = backing.value.toMutableMap().apply {
            remove(appId)
        }
    }

    override suspend fun clear() {
        backing.value = emptyMap()
    }
}
