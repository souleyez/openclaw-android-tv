package com.openclaw.tv.feature.home

import com.openclaw.tv.core.storage.StoredEntitlementSnapshot
import com.openclaw.tv.core.storage.StoredEntitlementSummary
import com.openclaw.tv.core.storage.StoredResourceSession
import com.openclaw.tv.core.storage.StoredRuntimeApp
import com.openclaw.tv.core.storage.StoredRuntimeManifest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal class HomeRuntimePresenter(
    private val adSlotRegistry: HomeAdSlotRegistry = HomeAdSlotRegistry(),
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {

    fun fallbackRuntimeManifest(): ResolvedRuntimeManifest {
        return ResolvedRuntimeManifest(
            manifestVersion = "",
            countryCode = null,
            regionCode = null,
            source = RuntimeManifestSource.FALLBACK,
            featuredApps = HomeAppCatalog.defaultFeaturedApps().map { app ->
                RuntimeFeaturedApp(
                    appId = app.id,
                    title = app.title,
                    packageName = app.packageName,
                    summary = app.summary,
                    monogram = app.monogram,
                    accentColorHex = app.accentColorHex,
                    installMode = "prompt",
                    requiresEntitlement = false,
                )
            },
            ignoredFeaturedAppIds = emptyList(),
            heroAds = emptyList(),
        )
    }

    fun presentRuntimeManifest(manifest: StoredRuntimeManifest?): ResolvedRuntimeManifest {
        if (manifest == null) {
            return fallbackRuntimeManifest()
        }
        val featuredResolution = resolveFeaturedApps(manifest.apps)
        return ResolvedRuntimeManifest(
            manifestVersion = manifest.manifestVersion.trim(),
            countryCode = manifest.countryCode.trim().takeIf(String::isNotBlank),
            regionCode = manifest.regionCode?.trim()?.takeIf(String::isNotBlank),
            source = RuntimeManifestSource.CACHE,
            featuredApps = featuredResolution.featuredApps,
            ignoredFeaturedAppIds = featuredResolution.ignoredIds,
            heroAds = adSlotRegistry.resolveHeroAds(
                adSlots = manifest.adSlots,
                nowIso = currentUtcIsoString(),
            ),
        )
    }

    fun presentEntitlement(summary: StoredEntitlementSummary?): ResolvedEntitlementSummary? {
        summary ?: return null
        return ResolvedEntitlementSummary(
            planCode = summary.planCode.trim(),
            paymentState = summary.paymentState.normalizedPaymentState(),
            priorityClass = summary.priorityClass.trim(),
            renewalState = summary.renewalState.trim(),
            source = EntitlementSource.CACHE,
        )
    }

    fun presentResourceSession(session: StoredResourceSession?): ResolvedResourceSession? {
        session ?: return null
        return ResolvedResourceSession(
            resourceSessionId = session.resourceSessionId.trim(),
            queueStatus = session.queueStatus.normalizedQueueStatus(),
            priorityClass = session.priorityClass.trim(),
            queuePosition = session.queuePosition,
            estimatedWaitSeconds = session.estimatedWaitSeconds,
            expiresAt = session.expiresAt?.trim()?.takeIf(String::isNotBlank),
            updatedAt = session.updatedAt.trim(),
            hasAppAccountLease = session.appAccountLease != null,
            hasModelLease = session.modelLease != null,
            entitlementSummary = presentEmbeddedEntitlement(session.entitlementSummary),
            source = ResourceSessionSource.CACHE,
        )
    }

    private fun presentEmbeddedEntitlement(summary: StoredEntitlementSnapshot): ResolvedEntitlementSummary {
        return ResolvedEntitlementSummary(
            planCode = summary.planCode.trim(),
            paymentState = summary.paymentState.normalizedPaymentState(),
            priorityClass = summary.priorityClass.trim(),
            renewalState = summary.renewalState.trim(),
            source = EntitlementSource.CACHE,
        )
    }

    private fun resolveFeaturedApps(apps: List<StoredRuntimeApp>): FeaturedAppResolution {
        val featuredApps = mutableListOf<RuntimeFeaturedApp>()
        val ignoredIds = mutableListOf<String>()
        apps.forEach { app ->
            if (!app.visibility.equals("featured", ignoreCase = true)) {
                return@forEach
            }
            val normalizedAppId = app.appId.trim().ifBlank { app.packageName.trim() }
            val normalizedTitle = app.title.trim()
            val normalizedPackageName = app.packageName.trim()
            if (normalizedAppId.isBlank() || normalizedTitle.isBlank() || normalizedPackageName.isBlank()) {
                ignoredIds += normalizedAppId.ifBlank { "(missing-app-id)" }
                return@forEach
            }
            val decoration = HomeAppCatalog.decorationFor(
                appId = normalizedAppId,
                packageName = normalizedPackageName,
            )
            featuredApps += RuntimeFeaturedApp(
                appId = normalizedAppId,
                title = normalizedTitle,
                packageName = normalizedPackageName,
                summary = decoration?.summary ?: HomeAppCatalog.fallbackSummary(
                    title = normalizedTitle,
                    requiresEntitlement = app.requiresEntitlement,
                ),
                monogram = decoration?.monogram ?: HomeAppCatalog.fallbackMonogram(
                    title = normalizedTitle,
                    packageName = normalizedPackageName,
                ),
                accentColorHex = decoration?.accentColorHex ?: HomeAppCatalog.fallbackAccentColor(
                    appId = normalizedAppId,
                    packageName = normalizedPackageName,
                ),
                installMode = app.installMode.trim(),
                requiresEntitlement = app.requiresEntitlement,
            )
        }
        return FeaturedAppResolution(
            featuredApps = featuredApps,
            ignoredIds = ignoredIds.distinct(),
        )
    }

    private fun currentUtcIsoString(): String {
        return ISO_UTC_FORMAT.format(Date(nowEpochMs()))
    }

    private fun String.normalizedQueueStatus(): String {
        return trim().lowercase().ifBlank { "not_requested" }
    }

    private data class FeaturedAppResolution(
        val featuredApps: List<RuntimeFeaturedApp>,
        val ignoredIds: List<String>,
    )

    private companion object {
        val ISO_UTC_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
}
