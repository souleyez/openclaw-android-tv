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

    suspend fun load(
        sessionToken: String,
        pollAfterSeconds: Int,
    ): RuntimeManifestSnapshot {
        val cached = manifestStore.read()
        val now = nowEpochMs()
        val refreshDecision = scheduler.evaluate(
            lastFetchedAtEpochMs = cached?.cachedAtEpochMs,
            pollAfterSeconds = pollAfterSeconds,
            nowEpochMs = now,
        )
        if (!refreshDecision.shouldRefresh) {
            return RuntimeManifestSnapshot(
                manifest = cached,
                source = RuntimeManifestSnapshotSource.CACHE,
                refreshed = false,
                nextRefreshAtEpochMs = refreshDecision.nextRefreshAtEpochMs,
            )
        }

        return try {
            val refreshedManifest = platformApi.getRuntimeManifest(sessionToken)
                .toStoredRuntimeManifest(cachedAtEpochMs = now)
            manifestStore.save(refreshedManifest)
            RuntimeManifestSnapshot(
                manifest = refreshedManifest,
                source = RuntimeManifestSnapshotSource.REMOTE,
                refreshed = true,
                nextRefreshAtEpochMs = scheduler.nextRefreshAtEpochMs(
                    lastFetchedAtEpochMs = now,
                    pollAfterSeconds = pollAfterSeconds,
                ),
            )
        } catch (_: Exception) {
            if (cached != null) {
                RuntimeManifestSnapshot(
                    manifest = cached,
                    source = RuntimeManifestSnapshotSource.CACHE,
                    refreshed = false,
                    nextRefreshAtEpochMs = scheduler.nextRefreshAtEpochMs(
                        lastFetchedAtEpochMs = cached.cachedAtEpochMs,
                        pollAfterSeconds = pollAfterSeconds,
                    ),
                )
            } else {
                RuntimeManifestSnapshot(
                    manifest = null,
                    source = RuntimeManifestSnapshotSource.EMPTY,
                    refreshed = false,
                    nextRefreshAtEpochMs = null,
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
