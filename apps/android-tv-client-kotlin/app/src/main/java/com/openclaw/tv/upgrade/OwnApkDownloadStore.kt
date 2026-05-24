package com.openclaw.tv.upgrade

import android.content.Context
import org.json.JSONObject

data class StoredOwnApkUpdate(
    val releaseId: String,
    val updateMode: String,
    val targetVersionCode: Long,
    val targetVersionName: String = "",
    val downloadUrl: String = "",
    val sha256: String = "",
    val size: Long = 0L,
    val installPolicy: String = "",
    val downloadId: Long? = null,
    val localPath: String = "",
    val status: String = "",
    val progressPercent: Int? = null,
    val errorMessage: String? = null,
    val updatedAtEpochMs: Long = 0L,
)

interface OwnApkDownloadStore {
    suspend fun read(): StoredOwnApkUpdate?
    suspend fun write(update: StoredOwnApkUpdate)
    suspend fun clear()
}

class InMemoryOwnApkDownloadStore(
    initial: StoredOwnApkUpdate? = null,
) : OwnApkDownloadStore {
    private var value: StoredOwnApkUpdate? = initial

    override suspend fun read(): StoredOwnApkUpdate? = value

    override suspend fun write(update: StoredOwnApkUpdate) {
        value = update
    }

    override suspend fun clear() {
        value = null
    }
}

class SharedPreferencesOwnApkDownloadStore(
    context: Context,
) : OwnApkDownloadStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        "openclaw_own_apk_update_store",
        Context.MODE_PRIVATE,
    )

    override suspend fun read(): StoredOwnApkUpdate? {
        return preferences.getString(OwnApkUpdateJsonKey, null)
            ?.let(::decode)
    }

    override suspend fun write(update: StoredOwnApkUpdate) {
        preferences.edit()
            .putString(OwnApkUpdateJsonKey, encode(update))
            .apply()
    }

    override suspend fun clear() {
        preferences.edit()
            .remove(OwnApkUpdateJsonKey)
            .apply()
    }

    private fun encode(update: StoredOwnApkUpdate): String {
        return JSONObject()
            .put("releaseId", update.releaseId)
            .put("updateMode", update.updateMode)
            .put("targetVersionCode", update.targetVersionCode)
            .put("targetVersionName", update.targetVersionName)
            .put("downloadUrl", update.downloadUrl)
            .put("sha256", update.sha256)
            .put("size", update.size)
            .put("installPolicy", update.installPolicy)
            .put("downloadId", update.downloadId)
            .put("localPath", update.localPath)
            .put("status", update.status)
            .put("progressPercent", update.progressPercent)
            .put("errorMessage", update.errorMessage)
            .put("updatedAtEpochMs", update.updatedAtEpochMs)
            .toString()
    }

    private fun decode(encoded: String): StoredOwnApkUpdate? {
        return runCatching {
            val json = JSONObject(encoded)
            StoredOwnApkUpdate(
                releaseId = json.optString("releaseId"),
                updateMode = json.optString("updateMode"),
                targetVersionCode = json.optLong("targetVersionCode"),
                targetVersionName = json.optString("targetVersionName"),
                downloadUrl = json.optString("downloadUrl"),
                sha256 = json.optString("sha256"),
                size = json.optLong("size"),
                installPolicy = json.optString("installPolicy"),
                downloadId = json.optLongOrNull("downloadId"),
                localPath = json.optString("localPath"),
                status = json.optString("status"),
                progressPercent = json.optIntOrNull("progressPercent"),
                errorMessage = json.optStringOrNull("errorMessage"),
                updatedAtEpochMs = json.optLong("updatedAtEpochMs"),
            )
        }.getOrNull()
    }

    private fun JSONObject.optLongOrNull(name: String): Long? {
        if (!has(name) || isNull(name)) {
            return null
        }
        return optLong(name).takeIf { it > 0L }
    }

    private fun JSONObject.optIntOrNull(name: String): Int? {
        if (!has(name) || isNull(name)) {
            return null
        }
        return optInt(name).coerceIn(0, 100)
    }

    private fun JSONObject.optStringOrNull(name: String): String? {
        if (!has(name) || isNull(name)) {
            return null
        }
        return optString(name).trim().takeIf(String::isNotBlank)
    }

    private companion object {
        const val OwnApkUpdateJsonKey = "own_apk_update_json"
    }
}
