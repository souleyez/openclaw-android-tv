package com.openclaw.tv.runtime

import com.openclaw.tv.BuildConfig
import com.openclaw.tv.core.network.TvRuntimeRequestContext
import java.util.Locale

internal class TvRuntimeRequestContextResolver(
    private val countryOverride: String = BuildConfig.OPENCLAW_COUNTRY_CODE,
    private val regionOverride: String = BuildConfig.OPENCLAW_REGION_CODE,
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
