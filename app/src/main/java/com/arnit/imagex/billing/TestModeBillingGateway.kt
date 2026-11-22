package com.arnit.imagex.billing

import com.arnit.imagex.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Test-mode billing gateway. It never contacts a server and never fakes a
 * real purchase: enabling Premium here happens only through an explicit
 * developer toggle in Settings, and the whole app marks the state
 * "TEST MODE" so nobody mistakes it for a paid entitlement.
 */
class TestModeBillingGateway(
    private val settings: SettingsRepository,
    private val scope: CoroutineScope
) : BillingGateway {

    private val _state = MutableStateFlow(
        BillingState(configured = false, testMode = true, unlocked = false, plan = null)
    )
    override val state: StateFlow<BillingState> = _state

    init {
        scope.launch { refresh() }
    }

    suspend fun refresh() {
        val current = settings.current()
        emitFrom(current.billingTestMode, settings.premiumPlan())
    }

    private suspend fun emitFrom(testMode: Boolean, planName: String?) {
        val plan = parsePlan(planName ?: "")
        _state.value = BillingState(
            configured = false,
            testMode = testMode,
            unlocked = testMode && plan != null,
            plan = plan,
            products = defaultProducts(testMode),
            message = if (testMode) {
                "TEST MODE - no real purchases are possible in this build. " +
                    "Google Play Billing product IDs and credentials are not configured."
            } else {
                "Google Play Billing is not configured for this build. " +
                    "Premium purchases are unavailable; test mode can be enabled in Settings."
            }
        )
    }

    override suspend fun purchase(plan: PlanId): BillingState {
        val current = settings.current()
        if (!current.billingTestMode) {
            refresh()
            return _state.value.copy(message = "Purchases require a configured Google Play Billing build.")
        }
        val planName = when (plan) {
            PlanId.Monthly -> "MONTHLY"
            PlanId.Yearly -> "YEARLY"
            PlanId.Lifetime -> "LIFETIME"
            PlanId.Free -> return _state.value
        }
        settings.setPremiumPlan(planName, testMode = true)
        refresh()
        return _state.value
    }

    override suspend fun restore(): BillingState {
        refresh()
        return _state.value
    }

    suspend fun cancelTestPurchase() {
        settings.setPremiumPlan(null, testMode = false)
        refresh()
    }

    fun toggleTestMode(enabled: Boolean) {
        scope.launch {
            settings.setBillingTestMode(enabled)
            if (!enabled) settings.setPremiumPlan(null, testMode = false)
            refresh()
        }
    }

    private fun parsePlan(name: String): PlanId? = when (name) {
        "MONTHLY" -> PlanId.Monthly
        "YEARLY" -> PlanId.Yearly
        "LIFETIME" -> PlanId.Lifetime
        else -> null
    }

    private fun defaultProducts(testMode: Boolean): List<BillingProductInfo> = listOf(
        BillingProductInfo(PlanId.Monthly, if (testMode) "test" else "unavailable", "All Premium features for one month."),
        BillingProductInfo(PlanId.Yearly, if (testMode) "test" else "unavailable", "All Premium features for one year - best value."),
        BillingProductInfo(PlanId.Lifetime, if (testMode) "test" else "unavailable", "Pay once, keep Premium forever.")
    )
}
