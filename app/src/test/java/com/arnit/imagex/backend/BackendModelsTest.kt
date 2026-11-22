package com.arnit.imagex.backend

import org.junit.Assert.assertEquals
import org.junit.Test

class BackendModelsTest {
    @Test fun premiumStatusDefaults() {
        assertEquals(false, PremiumStatus(premium = false).premium)
        assertEquals(null, PremiumStatus(premium = false).plan)
    }

    @Test fun paymentOrderKeepsSandboxEnvironment() {
        val order = PaymentOrder("order-1", "session-1", 99.0, "INR", "sandbox")
        assertEquals("order-1", order.orderId)
        assertEquals("sandbox", order.environment)
        assertEquals(99.0, order.amount, 0.0)
    }
}
