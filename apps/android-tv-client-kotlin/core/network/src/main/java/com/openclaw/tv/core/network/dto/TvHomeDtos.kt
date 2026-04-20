package com.openclaw.tv.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TvHomeConfigDto(
    val id: String = "",
    val countryCode: String = "GLOBAL",
    val regionCode: String? = null,
    val backgroundImageUrl: String? = null,
    @SerialName("featuredAppIds")
    val featuredAppIds: List<String> = emptyList(),
    val status: String = "draft",
    val version: Int = 0,
    val createdAt: String = "",
    val updatedAt: String = "",
)
