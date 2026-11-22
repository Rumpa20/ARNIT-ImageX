package com.arnit.imagex.billing

/**
 * Billing architecture. Product IDs and merchant credentials are not bundled
 * with this build, so no real Google Play Billing calls are wired up - and no
 * purchase is ever fabricated. This layer defines the exact contract a real
 * implementation must satisfy (see docs/BILLING.md) and ships a clearly
 * labelled test-mode gateway that lets the app be exercised end-to-end.
 */
sealed class PlanId(val displayName: String, val isLifetime: Boolean = false) {
    object Free : PlanId("Free")
    object Monthly : PlanId("Monthly")
    object Yearly : PlanId("Yearly")
    object Lifetime : PlanId("Lifetime", isLifetime = true)
}

data class BillingProductInfo(
    val plan: PlanId,
    val priceText: String,
    val description: String
)

data class BillingState(
    val configured: Boolean,
    val testMode: Boolean,
    val unlocked: Boolean,
    val plan: PlanId?,
    val products: List<BillingProductInfo> = emptyList(),
    val message: String? = null
)

interface BillingGateway {
    val state: kotlinx.coroutines.flow.StateFlow<BillingState>

    /**
     * Attempts a purchase flow for the given plan. Implementations backed by
     * Google Play Billing launch the real flow; the test gateway refuses to
     * pretend and requires explicit opt-in through the settings toggle.
     */
    suspend fun purchase(plan: PlanId): BillingState

    suspend fun restore(): BillingState
}
