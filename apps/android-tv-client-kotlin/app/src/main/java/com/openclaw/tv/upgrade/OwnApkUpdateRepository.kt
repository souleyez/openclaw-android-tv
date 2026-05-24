package com.openclaw.tv.upgrade

import com.openclaw.tv.core.network.PlatformApi
import com.openclaw.tv.core.network.dto.OwnApkUpdateManifestDto
import com.openclaw.tv.core.network.dto.OwnApkUpdateReportEnvelope
import com.openclaw.tv.core.network.dto.OwnApkUpdateReportRequestDto

interface OwnApkUpdateRepository {
    suspend fun fetchManifest(
        sessionToken: String,
        currentVersionCode: Long,
        currentResourceVersion: String? = null,
    ): OwnApkUpdateManifestDto

    suspend fun report(
        sessionToken: String,
        request: OwnApkUpdateReportRequestDto,
    ): OwnApkUpdateReportEnvelope
}

class PlatformOwnApkUpdateRepository(
    private val platformApi: PlatformApi,
) : OwnApkUpdateRepository {

    override suspend fun fetchManifest(
        sessionToken: String,
        currentVersionCode: Long,
        currentResourceVersion: String?,
    ): OwnApkUpdateManifestDto {
        return platformApi.getOwnApkUpdateManifest(
            sessionToken = sessionToken,
            currentVersionCode = currentVersionCode,
            currentResourceVersion = currentResourceVersion,
        )
    }

    override suspend fun report(
        sessionToken: String,
        request: OwnApkUpdateReportRequestDto,
    ): OwnApkUpdateReportEnvelope {
        return platformApi.postOwnApkUpdateReport(
            sessionToken = sessionToken,
            request = request,
        )
    }
}
