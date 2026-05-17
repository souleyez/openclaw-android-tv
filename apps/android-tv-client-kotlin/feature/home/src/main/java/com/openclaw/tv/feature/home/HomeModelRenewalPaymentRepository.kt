package com.openclaw.tv.feature.home

import com.openclaw.tv.core.network.PlatformApi
import com.openclaw.tv.core.network.dto.TvEntitlementSummaryDto
import com.openclaw.tv.core.network.dto.TvModelRenewalPaymentOrderDto
import com.openclaw.tv.core.network.dto.TvModelRenewalPaymentOrderRequestDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

internal data class ResolvedModelRenewalPaymentOrder(
    val orderId: String,
    val title: String,
    val paymentProvider: String,
    val paymentState: String,
    val amountDisplay: String,
    val durationLabel: String,
    val qrCodeUrl: String,
    val qrExpiresAt: String,
    val entitlementSummary: ResolvedEntitlementSummary,
    val updatedAt: String,
)

internal open class HomeModelRenewalPaymentRepository(
    private val platformApi: PlatformApi,
    private val requestTimeoutMillis: Long = DEFAULT_REQUEST_TIMEOUT_MILLIS,
) {

    open suspend fun createOrder(
        sessionToken: String,
        sku: String,
    ): ResolvedModelRenewalPaymentOrder? {
        return try {
            withTimeout(requestTimeoutMillis) {
                platformApi.createModelRenewalPaymentOrder(
                    sessionToken = sessionToken,
                    request = TvModelRenewalPaymentOrderRequestDto(sku = sku),
                ).order.toResolvedModelRenewalPaymentOrder()
            }
        } catch (error: Exception) {
            error.rethrowIfExternalCancellation()
            null
        }
    }

    open suspend fun loadOrder(
        sessionToken: String,
        orderId: String,
    ): ResolvedModelRenewalPaymentOrder? {
        return try {
            withTimeout(requestTimeoutMillis) {
                platformApi.getModelRenewalPaymentOrderStatus(sessionToken, orderId).order.toResolvedModelRenewalPaymentOrder()
            }
        } catch (error: Exception) {
            error.rethrowIfExternalCancellation()
            null
        }
    }

    private companion object {
        const val DEFAULT_REQUEST_TIMEOUT_MILLIS = 1_500L
    }
}

private fun Throwable.rethrowIfExternalCancellation() {
    if (this is CancellationException && this !is TimeoutCancellationException) {
        throw this
    }
}

private fun TvModelRenewalPaymentOrderDto.toResolvedModelRenewalPaymentOrder(): ResolvedModelRenewalPaymentOrder {
    val resolvedAmount = amount.display.trim().takeIf(String::isNotBlank)
        ?: buildFallbackAmountLabel(
            currency = amount.currency,
            totalCents = amount.totalCents,
        )
    return ResolvedModelRenewalPaymentOrder(
        orderId = orderId.trim(),
        title = title.trim().ifBlank { "模型续费" },
        paymentProvider = paymentProvider.trim().lowercase().ifBlank { "wechat_pay" },
        paymentState = paymentState.normalizedPaymentState(),
        amountDisplay = resolvedAmount,
        durationLabel = buildDurationLabel(durationSeconds),
        qrCodeUrl = qr.codeUrl.trim(),
        qrExpiresAt = qr.expiresAt.trim(),
        entitlementSummary = entitlementSummary.toResolvedModelRenewalEntitlementSummary(),
        updatedAt = updatedAt.trim(),
    )
}

private fun buildDurationLabel(durationSeconds: Long): String {
    val days = durationSeconds / (24L * 60L * 60L)
    return if (days > 0L) {
        "${days}天"
    } else {
        "30天"
    }
}

private fun TvEntitlementSummaryDto.toResolvedModelRenewalEntitlementSummary(): ResolvedEntitlementSummary {
    return ResolvedEntitlementSummary(
        planCode = planCode.trim(),
        paymentState = paymentState.normalizedPaymentState(),
        priorityClass = priorityClass.trim(),
        renewalState = renewalState.trim(),
        source = EntitlementSource.REMOTE,
    )
}

private fun buildFallbackAmountLabel(
    currency: String,
    totalCents: Int,
): String {
    val normalizedCurrency = currency.trim().ifBlank { "CNY" }
    val whole = totalCents / 100
    val cents = kotlin.math.abs(totalCents % 100).toString().padStart(2, '0')
    return "$normalizedCurrency $whole.$cents"
}
