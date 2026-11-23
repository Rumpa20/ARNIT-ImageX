package com.arnit.imagex.payment

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.cashfree.pg.api.CFPaymentGatewayService
import com.cashfree.pg.core.api.CFSession
import com.cashfree.pg.core.api.CFSession.CFSessionBuilder
import com.cashfree.pg.core.api.CFTheme.CFThemeBuilder
import com.cashfree.pg.core.api.callback.CFCheckoutResponseCallback
import com.cashfree.pg.core.api.exception.CFException
import com.cashfree.pg.core.api.utils.CFErrorResponse
import com.cashfree.pg.ui.api.CFDropCheckoutPayment.CFDropCheckoutPaymentBuilder

/**
 * Cashfree Drop Checkout launcher.
 * The app must create the order on its authenticated backend first.
 * Payment callbacks are not proof of payment; caller must verify with backend.
 */
class CashfreeCheckoutActivity : AppCompatActivity(), CFCheckoutResponseCallback {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val orderId = intent.getStringExtra(EXTRA_ORDER_ID).orEmpty()
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID).orEmpty()
        if (orderId.isBlank() || sessionId.isBlank()) {
            finishWith(RESULT_INVALID, orderId)
            return
        }

        try {
            CFPaymentGatewayService.getInstance().setCheckoutCallback(this)
            val session: CFSession = CFSessionBuilder()
                .setEnvironment(CFSession.Environment.SANDBOX)
                .setPaymentSessionID(sessionId)
                .setOrderId(orderId)
                .build()
            val theme = CFThemeBuilder()
                .setNavigationBarBackgroundColor("#101820")
                .setNavigationBarTextColor("#FFFFFF")
                .setButtonBackgroundColor("#2563EB")
                .setButtonTextColor("#FFFFFF")
                .setPrimaryTextColor("#111827")
                .setSecondaryTextColor("#4B5563")
                .build()
            val checkout = CFDropCheckoutPaymentBuilder()
                .setSession(session)
                .setCFNativeCheckoutUITheme(theme)
                .build()
            CFPaymentGatewayService.getInstance().doPayment(this, checkout)
        } catch (e: CFException) {
            Log.e(TAG, "Unable to launch Cashfree checkout", e)
            finishWith(RESULT_ERROR, orderId)
        } catch (e: Exception) {
            Log.e(TAG, "Cashfree checkout error", e)
            finishWith(RESULT_ERROR, orderId)
        }
    }

    override fun onPaymentVerify(orderID: String) {
        // This callback is not proof of payment. Caller must verify with backend.
        finishWith(RESULT_VERIFY, orderID)
    }

    override fun onPaymentFailure(cfErrorResponse: CFErrorResponse, orderID: String) {
        Log.w(TAG, "Cashfree checkout reported failure: ${cfErrorResponse.message}")
        finishWith(RESULT_FAILURE, orderID)
    }

    private fun finishWith(result: String, orderId: String) {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_RESULT, result).putExtra(EXTRA_ORDER_ID, orderId))
        finish()
    }

    companion object {
        const val EXTRA_ORDER_ID = "com.arnit.imagex.payment.ORDER_ID"
        const val EXTRA_SESSION_ID = "com.arnit.imagex.payment.SESSION_ID"
        const val EXTRA_RESULT = "com.arnit.imagex.payment.RESULT"
        const val RESULT_VERIFY = "VERIFY_BACKEND"
        const val RESULT_FAILURE = "PAYMENT_FAILURE"
        const val RESULT_ERROR = "CHECKOUT_ERROR"
        const val RESULT_INVALID = "INVALID_ORDER"
        private const val TAG = "ImageXCashfree"
    }
}
