package com.openclaw.tv.feature.cast

data class DlnaRendererConfig(
    val uuid: String,
    val deviceName: String,
    val manufacturer: String = "OpenClaw",
    val modelName: String = "RS AITV",
    val modelNumber: String = "0.1.0",
)

data class DlnaRendererState(
    val displayName: String,
    val isRunning: Boolean,
    val descriptionUrl: String? = null,
    val errorMessage: String? = null,
)

data class DlnaMediaRequest(
    val uri: String,
    val metadata: String?,
)
