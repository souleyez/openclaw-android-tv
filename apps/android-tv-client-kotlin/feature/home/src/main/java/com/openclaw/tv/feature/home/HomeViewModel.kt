package com.openclaw.tv.feature.home

import androidx.lifecycle.ViewModel
import com.openclaw.tv.core.capability.CapabilitySnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FeaturedAppItem(
    val title: String,
    val packageName: String,
    val summary: String,
    val installed: Boolean,
)

data class HomeUiState(
    val title: String,
    val subtitle: String,
    val capabilityLabel: String,
    val primaryActionLabel: String,
    val featuredApps: List<FeaturedAppItem>,
)

class HomeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(defaultState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun bindCapabilities(snapshot: CapabilitySnapshot) {
        _uiState.value = defaultState(snapshot)
    }

    private fun defaultState(snapshot: CapabilitySnapshot? = null): HomeUiState {
        val vendorAvailable = listOf(
            snapshot?.hasVendorVoiceService,
            snapshot?.hasVendorMediaService,
            snapshot?.hasVendorProjectorService,
            snapshot?.hasVendorDeviceOpsService,
        ).any { it == true }
        return HomeUiState(
            title = "OpenClaw TV Baseline",
            subtitle = if (vendorAvailable) {
                "已检测到扩展系统能力，可继续接入更深控制。"
            } else {
                "基础版：仅支持首页展示、状态展示和显式应用跳转。"
            },
            capabilityLabel = if (vendorAvailable) "扩展能力可接入" else "基础版",
            primaryActionLabel = "查看应用",
            featuredApps = RecommendedApps.map { app ->
                FeaturedAppItem(
                    title = app.title,
                    packageName = app.packageName,
                    summary = if (snapshot?.isAppInstalled(app.packageName) == true) {
                        "${app.summary} · 已安装"
                    } else {
                        "${app.summary} · 未安装"
                    },
                    installed = snapshot?.isAppInstalled(app.packageName) == true,
                )
            },
        )
    }

    private data class RecommendedApp(
        val title: String,
        val packageName: String,
        val summary: String,
    )

    private companion object {
        val RecommendedApps = listOf(
            RecommendedApp("YouTube", "com.google.android.youtube.tv", "全球通用视频入口"),
            RecommendedApp("Netflix", "com.netflix.ninja", "主流 TV 节目播放"),
            RecommendedApp("Prime Video", "com.amazon.amazonvideo.livingroom", "海外影视内容"),
            RecommendedApp("Disney+", "com.disney.disneyplus", "家庭向内容"),
            RecommendedApp("Spotify", "com.spotify.tv.android", "全球音频与播客入口"),
        )
    }
}
