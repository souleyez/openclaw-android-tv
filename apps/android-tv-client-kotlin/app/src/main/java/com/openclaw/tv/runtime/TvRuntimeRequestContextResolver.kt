package com.openclaw.tv.runtime

import com.openclaw.tv.BuildConfig
import com.openclaw.tv.core.network.TvRuntimeRequestContext
import java.util.Locale

internal class TvRuntimeRequestContextResolver(
    private val countryOverride: String = BuildConfig.OPENCLAW_COUNTRY_CODE,
    private val regionOverride: String = BuildConfig.OPENCLAW_REGION_CODE,
    private val distributionKey: String = BuildConfig.OPENCLAW_DISTRIBUTION_KEY,
    private val packageName: String = BuildConfig.APPLICATION_ID,
    private val localeProvider: () -> Locale = { Locale.getDefault() },
) {

    fun resolve(): TvRuntimeRequestContext {
        val countryCode = normalizeCountryCode(countryOverride)
            .ifBlank { normalizeCountryCode(localeProvider().country) }
            .ifBlank { "GLOBAL" }
        val regionCode = normalizeRegionCode(regionOverride).ifBlank { null }
        return TvRuntimeRequestContext(
            countryCode = countryCode,
            regionCode = regionCode,
            distributionKey = distributionKey.trim().ifBlank { null },
            packageName = packageName.trim().ifBlank { null },
        )
    }

    private fun normalizeCountryCode(value: String?): String {
        return value
            ?.trim()
            ?.uppercase(Locale.US)
            .orEmpty()
    }

    private fun normalizeRegionCode(value: String?): String {
        return value
            ?.trim()
            ?.uppercase(Locale.US)
            .orEmpty()
    }
}
