package com.openclaw.tv.feature.home

import com.openclaw.tv.core.network.PlatformApi
import com.openclaw.tv.core.network.dto.TvHomeConfigDto
import com.openclaw.tv.core.storage.StoredTvHomeConfig
import com.openclaw.tv.core.storage.TvHomeConfigStore
import kotlinx.coroutines.withTimeout
import java.util.Locale

internal data class CatalogApp(
    val id: String,
    val title: String,
    val packageName: String,
    val summary: String,
    val monogram: String,
    val accentColorHex: String,
)

internal data class ResolvedTvHomeConfig(
    val countryCode: String,
    val regionCode: String?,
    val backgroundImageUrl: String?,
    val source: ConfigSource,
    val featuredApps: List<CatalogApp>,
    val unresolvedFeaturedAppIds: List<String>,
)

internal enum class ConfigSource {
    REMOTE,
    CACHE,
    FALLBACK,
}

internal object HomeAppCatalog {
    internal data class Resolution(
        val featuredApps: List<CatalogApp>,
        val unresolvedIds: List<String>,
    )

    private val catalog = listOf(
        CatalogApp(
            id = "tencent_video",
            title = "腾讯视频",
            packageName = "com.ktcp.video",
            summary = "电视剧、综艺与长视频内容",
            monogram = "TX",
            accentColorHex = "#26C281",
        ),
        CatalogApp(
            id = "iqiyi",
            title = "爱奇艺",
            packageName = "com.qiyi.video.tv.ele",
            summary = "影视剧、动漫与少儿内容",
            monogram = "IQ",
            accentColorHex = "#6CE23A",
        ),
        CatalogApp(
            id = "youku",
            title = "优酷",
            packageName = "com.youku.iot",
            summary = "综艺、剧集与赛事直播",
            monogram = "YK",
            accentColorHex = "#4DA3FF",
        ),
        CatalogApp(
            id = "bilibili",
            title = "哔哩哔哩",
            packageName = "tv.danmaku.bili",
            summary = "番剧、纪录片与年轻向内容",
            monogram = "B",
            accentColorHex = "#FF7FAE",
        ),
        CatalogApp(
            id = "mango_tv",
            title = "芒果TV",
            packageName = "com.mgtv.tv",
            summary = "综艺、剧集与卫视节目",
            monogram = "MG",
            accentColorHex = "#FFB347",
        ),
        CatalogApp(
            id = "youtube",
            title = "YouTube",
            packageName = "com.google.android.youtube.tv",
            summary = "全球通用视频入口",
            monogram = "YT",
            accentColorHex = "#FF4E45",
        ),
        CatalogApp(
            id = "netflix",
            title = "Netflix",
            packageName = "com.netflix.ninja",
            summary = "主流 TV 节目播放",
            monogram = "N",
            accentColorHex = "#E50914",
        ),
        CatalogApp(
            id = "prime_video",
            title = "Prime Video",
            packageName = "com.amazon.amazonvideo.livingroom",
            summary = "海外影视内容",
            monogram = "PV",
            accentColorHex = "#3DAEFF",
        ),
        CatalogApp(
            id = "disney_plus",
            title = "Disney+",
            packageName = "com.disney.disneyplus",
            summary = "家庭向内容",
            monogram = "D+",
            accentColorHex = "#6D8CFF",
        ),
        CatalogApp(
            id = "spotify",
            title = "Spotify",
            packageName = "com.spotify.tv.android",
            summary = "全球音频与播客入口",
            monogram = "SP",
            accentColorHex = "#1ED760",
        ),
        CatalogApp(
            id = "plex",
            title = "Plex",
            packageName = "com.plexapp.android",
            summary = "跨区媒体与局域网播放",
            monogram = "PX",
            accentColorHex = "#F5B400",
        ),
    )

    private val catalogById = catalog.associateBy { it.id }
    private val defaultFeaturedIds = listOf(
        "tencent_video",
        "iqiyi",
        "youku",
        "bilibili",
        "mango_tv",
    )

    fun allPackageNames(): List<String> = catalog.map { it.packageName }

    fun defaultFeaturedApps(): List<CatalogApp> = defaultFeaturedIds.mapNotNull(catalogById::get)

    fun resolveFeaturedApps(appIds: List<String>): Resolution {
        val normalizedIds = appIds
            .map { it.trim().lowercase(Locale.US) }
            .filter(String::isNotBlank)
        return Resolution(
            featuredApps = normalizedIds.mapNotNull(catalogById::get),
            unresolvedIds = normalizedIds.filterNot(catalogById::containsKey).distinct(),
        )
    }
}

internal class TvHomeRepository(
    private val platformApi: PlatformApi,
    private val cacheStore: TvHomeConfigStore? = null,
    private val requestTimeoutMillis: Long = DEFAULT_REQUEST_TIMEOUT_MILLIS,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {

    suspend fun load(locale: Locale = Locale.getDefault()): ResolvedTvHomeConfig {
        val countryCode = locale.country
            .takeIf(String::isNotBlank)
            ?.uppercase(Locale.US)
            ?: "GLOBAL"
        val regionCode = locale.variant
            .takeIf(String::isNotBlank)
            ?.uppercase(Locale.US)

        return try {
            val response = withTimeout(requestTimeoutMillis) {
                platformApi.getTvHomeConfig(countryCode = countryCode, regionCode = regionCode)
            }
            val resolved = response.toResolvedConfig(
                defaultCountryCode = countryCode,
                source = ConfigSource.REMOTE,
            )
            cacheStore?.save(
                response.toStoredConfig(
                    defaultCountryCode = countryCode,
                    cachedAtEpochMs = nowEpochMs(),
                ),
            )
            resolved
        } catch (_: Exception) {
            cacheStore
                ?.read()
                ?.takeIf { it.matches(countryCode = countryCode, regionCode = regionCode) }
                ?.toResolvedConfig(source = ConfigSource.CACHE)
                ?: fallback(countryCode = countryCode, regionCode = regionCode)
        }
    }

    companion object {
        private const val DEFAULT_REQUEST_TIMEOUT_MILLIS = 1_500L

        fun fallback(
            countryCode: String = "GLOBAL",
            regionCode: String? = null,
        ): ResolvedTvHomeConfig {
            return ResolvedTvHomeConfig(
                countryCode = countryCode,
                regionCode = regionCode,
                backgroundImageUrl = null,
                source = ConfigSource.FALLBACK,
                featuredApps = HomeAppCatalog.defaultFeaturedApps(),
                unresolvedFeaturedAppIds = emptyList(),
            )
        }
    }
}

private fun TvHomeConfigDto.toResolvedConfig(
    defaultCountryCode: String,
    source: ConfigSource,
): ResolvedTvHomeConfig {
    val normalizedCountryCode = countryCode.ifBlank { defaultCountryCode }
    val normalizedRegionCode = regionCode?.trim()?.takeIf(String::isNotBlank)
    val resolution = HomeAppCatalog.resolveFeaturedApps(featuredAppIds)
    return ResolvedTvHomeConfig(
        countryCode = normalizedCountryCode,
        regionCode = normalizedRegionCode,
        backgroundImageUrl = backgroundImageUrl?.trim()?.takeIf(String::isNotBlank),
        source = source,
        featuredApps = resolution.featuredApps,
        unresolvedFeaturedAppIds = resolution.unresolvedIds,
    )
}

private fun TvHomeConfigDto.toStoredConfig(
    defaultCountryCode: String,
    cachedAtEpochMs: Long,
): StoredTvHomeConfig {
    return StoredTvHomeConfig(
        countryCode = countryCode.ifBlank { defaultCountryCode },
        regionCode = regionCode?.trim()?.takeIf(String::isNotBlank),
        backgroundImageUrl = backgroundImageUrl?.trim()?.takeIf(String::isNotBlank),
        featuredAppIds = featuredAppIds,
        cachedAtEpochMs = cachedAtEpochMs,
    )
}

private fun StoredTvHomeConfig.toResolvedConfig(source: ConfigSource): ResolvedTvHomeConfig {
    val resolution = HomeAppCatalog.resolveFeaturedApps(featuredAppIds)
    return ResolvedTvHomeConfig(
        countryCode = countryCode,
        regionCode = regionCode,
        backgroundImageUrl = backgroundImageUrl,
        source = source,
        featuredApps = resolution.featuredApps,
        unresolvedFeaturedAppIds = resolution.unresolvedIds,
    )
}

private fun StoredTvHomeConfig.matches(
    countryCode: String,
    regionCode: String?,
): Boolean {
    val normalizedCountryCode = countryCode.trim().uppercase(Locale.US)
    val normalizedRegionCode = regionCode?.trim()?.uppercase(Locale.US)
    return this.countryCode.trim().uppercase(Locale.US) == normalizedCountryCode &&
        this.regionCode?.trim()?.uppercase(Locale.US) == normalizedRegionCode
}
