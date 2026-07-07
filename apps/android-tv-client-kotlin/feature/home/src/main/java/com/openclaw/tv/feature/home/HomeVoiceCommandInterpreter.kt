package com.openclaw.tv.feature.home

import java.util.Locale

internal sealed interface HomeVoiceCommand {
    data class OpenApp(
        val title: String,
        val packageName: String,
    ) : HomeVoiceCommand

    data object OpenCast : HomeVoiceCommand
    data object OpenLocalApps : HomeVoiceCommand
    data object OpenServiceCenter : HomeVoiceCommand
    data object OpenSettings : HomeVoiceCommand
    data object Unknown : HomeVoiceCommand
}

internal class HomeVoiceCommandInterpreter(
    private val targets: List<HomeVoiceAppTarget> = defaultTargets(),
) {
    fun interpret(rawText: String): HomeVoiceCommand {
        val normalized = rawText.normalizeVoiceText()
        if (normalized.isBlank()) {
            return HomeVoiceCommand.Unknown
        }

        targets.firstOrNull { target ->
            target.aliases.any { alias -> normalized.contains(alias.normalizeVoiceText()) }
        }?.let { target ->
            return HomeVoiceCommand.OpenApp(
                title = target.title,
                packageName = target.packageName,
            )
        }

        return when {
            normalized.containsAny("投屏", "连接手机", "手机连接", "airplay", "dlna") -> HomeVoiceCommand.OpenCast
            normalized.containsAny("应用列表", "应用管理", "安装应用", "新增应用", "新装应用", "usb", "u盘") -> HomeVoiceCommand.OpenLocalApps
            normalized.containsAny("服务中心", "会员", "续费", "支付", "二维码") -> HomeVoiceCommand.OpenServiceCenter
            normalized.containsAny("设置", "系统设置", "网络设置", "wifi", "wi fi", "无线网络") -> HomeVoiceCommand.OpenSettings
            else -> HomeVoiceCommand.Unknown
        }
    }

    private fun String.containsAny(vararg needles: String): Boolean {
        return needles.any { needle -> contains(needle.normalizeVoiceText()) }
    }

    companion object {
        fun defaultTargets(): List<HomeVoiceAppTarget> {
            return listOf(
                HomeVoiceAppTarget(
                    title = "云视听小电视",
                    packageName = "com.xiaodianshi.tv.yst",
                    aliases = listOf("云视听小电视", "小电视", "b站", "bilibili", "哔哩哔哩"),
                ),
                HomeVoiceAppTarget(
                    title = "云视听极光",
                    packageName = "com.ktcp.tvvideo",
                    aliases = listOf("云视听极光", "极光", "腾讯视频", "腾讯"),
                ),
                HomeVoiceAppTarget(
                    title = "银河奇异果",
                    packageName = "com.gitvjisu.video",
                    aliases = listOf("银河奇异果", "奇异果", "爱奇艺", "iqiyi"),
                ),
                HomeVoiceAppTarget(
                    title = "CIBN酷喵",
                    packageName = "com.youku.iot",
                    aliases = listOf("酷喵", "优酷", "youku"),
                ),
                HomeVoiceAppTarget(
                    title = "芒果TV",
                    packageName = "com.starcor.mango",
                    aliases = listOf("芒果tv", "芒果", "mango", "mgtv"),
                ),
            )
        }
    }
}

internal data class HomeVoiceAppTarget(
    val title: String,
    val packageName: String,
    val aliases: List<String>,
)

private fun String.normalizeVoiceText(): String {
    return lowercase(Locale.getDefault())
        .replace(Regex("[\\s,，。.!！?？:：;；\"'“”‘’]"), "")
}
