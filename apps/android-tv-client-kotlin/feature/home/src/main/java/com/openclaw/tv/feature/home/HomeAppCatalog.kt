package com.openclaw.tv.feature.home

import java.util.Locale

internal data class CatalogApp(
    val id: String,
    val title: String,
    val packageName: String,
    val summary: String,
    val monogram: String,
    val accentColorHex: String,
)

internal object HomeAppCatalog {
    internal data class Resolution(
        val featuredApps: List<CatalogApp>,
        val unresolvedIds: List<String>,
    )

    private val catalog = listOf(
        CatalogApp(
            id = "xiaodianshi",
            title = "云视听小电视",
            packageName = "com.xiaodianshi.tv.yst",
            summary = "B站 TV 内容入口",
            monogram = "小",
            accentColorHex = "#35A7FF",
        ),
        CatalogApp(
            id = "tencent_video",
            title = "云视听极光",
            packageName = "com.ktcp.tvvideo",
            summary = "电视剧、综艺与长视频内容",
            monogram = "极",
            accentColorHex = "#26C281",
        ),
        CatalogApp(
            id = "iqiyi",
            title = "银河奇异果",
            packageName = "com.gitvjisu.video",
            summary = "影视剧、动漫与少儿内容",
            monogram = "奇",
            accentColorHex = "#6CE23A",
        ),
        CatalogApp(
            id = "youku",
            title = "CIBN酷喵",
            packageName = "com.youku.iot",
            summary = "优酷 TV 内容入口",
            monogram = "酷",
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
            packageName = "com.starcor.mango",
            summary = "综艺、剧集与卫视节目",
            monogram = "MG",
            accentColorHex = "#FFB347",
        ),
        CatalogApp(
            id = "dangbei_market",
            title = "当贝市场",
            packageName = "com.dangbeimarket",
            summary = "TV 应用安装与管理入口",
            monogram = "当",
            accentColorHex = "#5FB8FF",
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

    private val catalogById = catalog.associateBy { normalizeKey(it.id) }
    private val catalogByPackageName = catalog.associateBy { normalizeKey(it.packageName) }
    private val defaultFeaturedIds = listOf(
        "xiaodianshi",
        "tencent_video",
        "youku",
        "iqiyi",
        "mango_tv",
    )
    private val fallbackAccentPalette = listOf(
        "#4E89FF",
        "#26C281",
        "#FF9A57",
        "#F06EA5",
        "#7C6BFF",
        "#3DAEFF",
    )

    fun allPackageNames(): List<String> = catalog.map { it.packageName }

    fun defaultFeaturedApps(): List<CatalogApp> = defaultFeaturedIds.mapNotNull { decorationFor(appId = it) }

    fun decorationFor(appId: String? = null, packageName: String? = null): CatalogApp? {
        val normalizedAppId = appId?.let(::normalizeKey)
        val normalizedPackageName = packageName?.let(::normalizeKey)
        return normalizedAppId?.let(catalogById::get) ?: normalizedPackageName?.let(catalogByPackageName::get)
    }

    fun fallbackSummary(
        title: String,
        requiresEntitlement: Boolean,
    ): String {
        return if (requiresEntitlement) {
            "$title 需要授权后继续使用"
        } else {
            "$title 由 home 通过 runtime-manifest 分发"
        }
    }

    fun fallbackMonogram(title: String, packageName: String): String {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isNotEmpty()) {
            val firstChar = trimmedTitle.first()
            if (firstChar.code > 127) {
                return firstChar.toString()
            }
            val letters = trimmedTitle
                .split(Regex("[^A-Za-z0-9]+"))
                .filter(String::isNotBlank)
                .take(2)
                .map { token ->
                    token.first().uppercaseChar().toString()
                }
                .joinToString(separator = "")
            if (letters.isNotBlank()) {
                return letters
            }
        }
        return packageName
            .substringAfterLast('.')
            .take(2)
            .uppercase(Locale.US)
            .ifBlank { "TV" }
    }

    fun fallbackAccentColor(appId: String, packageName: String): String {
        val key = normalizeKey(appId.ifBlank { packageName })
        val paletteIndex = (key.hashCode() and Int.MAX_VALUE) % fallbackAccentPalette.size
        return fallbackAccentPalette[paletteIndex]
    }

    private fun normalizeKey(value: String): String {
        return value.trim().lowercase(Locale.US)
    }
}
