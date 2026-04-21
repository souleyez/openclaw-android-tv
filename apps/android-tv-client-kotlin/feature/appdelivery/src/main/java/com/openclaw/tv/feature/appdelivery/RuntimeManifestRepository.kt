package com.openclaw.tv.feature.appdelivery

import com.openclaw.tv.core.network.PlatformApi
import com.openclaw.tv.core.network.dto.TvRuntimeManifestDto
import com.openclaw.tv.core.storage.RuntimeManifestStore
import com.openclaw.tv.core.storage.StoredRuntimeAdCreative
import com.openclaw.tv.core.storage.StoredRuntimeAdSlot
import com.openclaw.tv.core.storage.StoredRuntimeApp
import com.openclaw.tv.core.storage.StoredRuntimeManifest

enum class RuntimeManifestSnapshotSource {
    REMOTE,
    CACHE,
    EMPTY,
}

data class RuntimeManifestSnapshot(
    val manifest: StoredRuntimeManifest?,
    val source: RuntimeManifestSnapshotSource,
    val refreshed: Boolean,
    val nextRefreshAtEpochMs: Long? = null,
)

class RuntimeManifestRepository(
    private val platformApi: PlatformApi,
    private val manifestStore: RuntimeManifestStore,
    private val scheduler: ManifestRefreshScheduler = ManifestRefreshScheduler(),
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {

    private var activeSessionToken: String? = null
    private var nextAllowedRefreshAtEpochMs: Long? = null

    suspend fun load(
        sessionToken: String,
        pollAfterSeconds: Int,
    ): RuntimeManifestSnapshot {
        val cached = manifestStore.read()
        val normalizedSessionToken = sessionToken.trim()
        val sessionChanged = activeSessionToken != null && activeSessionToken != normalizedSessionToken
        if (sessionChanged) {
            nextAllowedRefreshAtEpochMs = null
        }
        activeSessionToken = normalizedSessionToken
        val now = nowEpochMs()
        val persistedNextRefreshAtEpochMs = if (sessionChanged) {
            null
        } else {
            cached?.cachedAtEpochMs?.let { cachedAtEpochMs ->
                scheduler.nextRefreshAtEpochMs(
                    lastFetchedAtEpochMs = cachedAtEpochMs,
                    pollAfterSeconds = pollAfterSeconds,
                )
            }
        }
        val resolvedNextRefreshAtEpochMs = nextAllowedRefreshAtEpochMs ?: persistedNextRefreshAtEpochMs
        if (resolvedNextRefreshAtEpochMs != null && now < resolvedNextRefreshAtEpochMs) {
            return RuntimeManifestSnapshot(
                manifest = cached,
                source = if (cached != null) RuntimeManifestSnapshotSource.CACHE else RuntimeManifestSnapshotSource.EMPTY,
                refreshed = false,
                nextRefreshAtEpochMs = resolvedNextRefreshAtEpochMs,
            )
        }

        return try {
            val refreshedManifest = platformApi.getRuntimeManifest(normalizedSessionToken)
                .toStoredRuntimeManifest(cachedAtEpochMs = now)
            manifestStore.save(refreshedManifest)
            val nextRefreshAtEpochMs = scheduler.nextRefreshAtEpochMs(
                lastFetchedAtEpochMs = now,
                pollAfterSeconds = pollAfterSeconds,
            )
            nextAllowedRefreshAtEpochMs = nextRefreshAtEpochMs
            RuntimeManifestSnapshot(
                manifest = refreshedManifest,
                source = RuntimeManifestSnapshotSource.REMOTE,
                refreshed = true,
                nextRefreshAtEpochMs = nextRefreshAtEpochMs,
            )
        } catch (_: Exception) {
            val nextRefreshAtEpochMs = scheduler.nextRefreshAtEpochMs(
                lastFetchedAtEpochMs = now,
                pollAfterSeconds = pollAfterSeconds,
            )
            nextAllowedRefreshAtEpochMs = nextRefreshAtEpochMs
            if (cached != null) {
                RuntimeManifestSnapshot(
                    manifest = cached,
                    source = RuntimeManifestSnapshotSource.CACHE,
                    refreshed = false,
                    nextRefreshAtEpochMs = nextRefreshAtEpochMs,
                )
            } else {
                RuntimeManifestSnapshot(
                    manifest = null,
                    source = RuntimeManifestSnapshotSource.EMPTY,
                    refreshed = false,
                    nextRefreshAtEpochMs = nextRefreshAtEpochMs,
                )
            }
        }
    }
}

private fun TvRuntimeManifestDto.toStoredRuntimeManifest(
    cachedAtEpochMs: Long,
): StoredRuntimeManifest {
    return StoredRuntimeManifest(
        manifestVersion = manifestVersion.trim(),
        countryCode = countryCode.trim().ifBlank { "GLOBAL" },
        regionCode = regionCode?.trim()?.takeIf(String::isNotBlank),
        apps = apps.map { app ->
            StoredRuntimeApp(
                appId = app.appId.trim(),
                title = app.title.trim(),
                packageName = app.packageName.trim(),
                downloadUrl = app.downloadUrl.trim(),
                sha256 = app.sha256.trim(),
                versionCode = app.versionCode,
                versionName = app.versionName.trim(),
                minClientVersion = app.minClientVersion.trim(),
                installMode = app.installMode.trim(),
                visibility = app.visibility.trim(),
                preloadPolicy = app.preloadPolicy.trim(),
                requiresEntitlement = app.requiresEntitlement,
            )
        },
        adSlots = adSlots.map { slot ->
            StoredRuntimeAdSlot(
                slotId = slot.slotId.trim(),
                enabled = slot.enabled,
                creatives = slot.creatives.map { creative ->
                    StoredRuntimeAdCreative(
                        creativeId = creative.creativeId.trim(),
                        mediaType = creative.mediaType.trim(),
                        assetUrl = creative.assetUrl.trim(),
                        altText = creative.altText.trim(),
                        clickActionType = creative.clickActionType.trim(),
                        clickActionValue = creative.clickActionValue?.trim()?.takeIf(String::isNotBlank),
                        startsAt = creative.startsAt?.trim()?.takeIf(String::isNotBlank),
                        endsAt = creative.endsAt?.trim()?.takeIf(String::isNotBlank),
                    )
                },
            )
        },
        cachedAtEpochMs = cachedAtEpochMs,
    )
}
