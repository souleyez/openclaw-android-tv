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
data class StoredSession(
    val projectKey: String,
    val sessionToken: String,
    val expiresAt: String,
    val userId: String,
    val deviceId: String,
    val principalLabel: String,
)

interface SessionStore {
    val session: Flow<StoredSession?>
    suspend fun read(): StoredSession?
    suspend fun save(value: StoredSession)
    suspend fun clear()
}

private val Context.sessionDataStore by preferencesDataStore(name = "openclaw_session_store")

class DataStoreSessionStore(
    private val context: Context,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    },
) : SessionStore {

    override val session: Flow<StoredSession?> = context.sessionDataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { prefs ->
            prefs[SessionJsonKey]?.let { encoded ->
                json.decodeFromString(StoredSession.serializer(), encoded)
            }
        }

    override suspend fun read(): StoredSession? = session.first()

    override suspend fun save(value: StoredSession) {
        context.sessionDataStore.edit { prefs ->
            prefs[SessionJsonKey] = json.encodeToString(StoredSession.serializer(), value)
        }
    }

    override suspend fun clear() {
        context.sessionDataStore.edit { prefs ->
            prefs.remove(SessionJsonKey)
        }
    }

    private companion object {
        val SessionJsonKey: Preferences.Key<String> = stringPreferencesKey("session_json")
    }
}

class InMemorySessionStore(initial: StoredSession? = null) : SessionStore {
    private val backing = MutableStateFlow(initial)

    override val session: Flow<StoredSession?> = backing

    override suspend fun read(): StoredSession? = backing.value

    override suspend fun save(value: StoredSession) {
        backing.value = value
    }

    override suspend fun clear() {
        backing.value = null
    }
}
