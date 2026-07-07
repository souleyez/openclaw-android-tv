package com.openclaw.tv.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class TvVoiceCommandRequestDto(
    val transcript: String,
    val locale: String = "zh-CN",
    val availableApps: List<TvVoiceCommandAppDto> = emptyList(),
)

@Serializable
data class TvVoiceCommandAppDto(
    val title: String,
    val packageName: String,
    val aliases: List<String> = emptyList(),
)

@Serializable
data class TvVoiceCommandEnvelopeDto(
    val status: String = "",
    val command: TvVoiceCommandDto = TvVoiceCommandDto(),
)

@Serializable
data class TvVoiceCommandDto(
    val action: String = "none",
    val reply: String = "",
    val targetTitle: String = "",
    val packageName: String = "",
    val confidence: Double = 0.0,
)

@Serializable
data class ModelProxyChatCompletionRequestDto(
    val messages: List<ModelProxyChatMessageDto>,
    val temperature: Double = 0.1,
    @SerialName("max_tokens")
    val maxTokens: Int = 256,
)

@Serializable
data class ModelProxyChatMessageDto(
    val role: String,
    val content: String,
)

@Serializable
data class ModelProxyChatCompletionEnvelopeDto(
    val choices: List<ModelProxyChatChoiceDto> = emptyList(),
)

@Serializable
data class ModelProxyChatChoiceDto(
    val index: Int = 0,
    val message: ModelProxyChatMessageDto = ModelProxyChatMessageDto(role = "", content = ""),
    @SerialName("finish_reason")
    val finishReason: String? = null,
)

fun TvVoiceCommandRequestDto.toModelProxyChatCompletionRequest(): ModelProxyChatCompletionRequestDto {
    val appsPrompt = availableApps.joinToString(separator = "\n") { app ->
        val aliases = app.aliases.joinToString(separator = "、").ifBlank { app.title }
        "- ${app.title} package=${app.packageName} aliases=$aliases"
    }.ifBlank {
        "- 芒果TV package=com.starcor.mango aliases=芒果、芒果TV、mgtv"
    }
    val systemPrompt = """
        你是 OpenClaw Android TV 首页语音指令解析器。
        只输出一个 JSON 对象，不要 Markdown，不要解释。
        JSON 字段:
        action: open_app | open_cast | open_local_apps | open_service_center | open_settings | none
        reply: 面向电视用户的一句简短中文反馈，20字以内
        targetTitle: action=open_app 时填写应用名，否则空字符串
        packageName: action=open_app 时只能填写可用应用列表里的 package，否则空字符串
        confidence: 0到1之间的小数
        可用应用:
        $appsPrompt
        规则:
        - 用户要投屏、手机连接、AirPlay 或 DLNA 时返回 open_cast。
        - 用户要安装、升级、删除、USB、U盘或应用管理时返回 open_local_apps。
        - 用户要会员、AI服务、续费、支付或二维码时返回 open_service_center。
        - 用户要系统设置、网络设置或 Wi-Fi 时返回 open_settings。
        - 不确定时返回 none。
    """.trimIndent()
    return ModelProxyChatCompletionRequestDto(
        messages = listOf(
            ModelProxyChatMessageDto(role = "system", content = systemPrompt),
            ModelProxyChatMessageDto(role = "user", content = transcript.trim()),
        ),
    )
}

fun ModelProxyChatCompletionEnvelopeDto.toTvVoiceCommandEnvelope(json: Json): TvVoiceCommandEnvelopeDto {
    val content = choices.firstOrNull()?.message?.content.orEmpty()
    val command = runCatching {
        json.decodeFromString(TvVoiceCommandDto.serializer(), content.extractJsonObject())
    }.getOrElse {
        TvVoiceCommandDto(
            action = "none",
            reply = "这句话我还不会执行。",
        )
    }
    return TvVoiceCommandEnvelopeDto(
        status = "ok",
        command = command,
    )
}

private fun String.extractJsonObject(): String {
    val trimmed = trim()
    if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
        return trimmed
    }
    val start = trimmed.indexOf('{')
    val end = trimmed.lastIndexOf('}')
    return if (start >= 0 && end > start) {
        trimmed.substring(start, end + 1)
    } else {
        "{}"
    }
}
