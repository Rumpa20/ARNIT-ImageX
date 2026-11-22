package com.arnit.imagex.billing.cashfree

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.cashfree.pg.api.CFPaymentGatewayService
import com.cashfree.pg.core.api.CFSession
import com.cashfree.pg.core.api.callback.CFCheckoutResponseCallback
import com.cashfree.pg.core.api.exception.CFException
import com.cashfree.pg.core.api.utils.CFErrorResponse
import com.cashfree.pg.ui.api.CFDropCheckoutPayment.CFDropCheckoutPaymentBuilder

/**
 * Starts Cashfree Drop Checkout using an order and payment session created by the trusted backend.
 * This Activity reports only a callback result; Premium must be enabled after backend verification.
 */
class CashfreeCheckoutActivity : ComponentActivity(), CFCheckoutResponseCallback {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val orderId = intent.getStringExtra(EXTRA_ORDER_ID).orEmpty()
        val paymentSessionId = intent.getStringExtra(EXTRA_PAYMENT_SESSION_ID).orEmpty()
        val environment = intent.getStringExtra(EXTRA_ENVIRONMENT).orEmpty()

        if (orderId.isBlank() || paymentSessionId.isBlank()) {
            Toast.makeText(this, "Invalid payment session.", Toast.LENGTH_LONG).show()
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        try {
            CFPaymentGatewayService.getInstance().setCheckoutCallback(this)
            val cfSession = CFSession.CFSessionBuilder()
                .setEnvironment(
                    if (environment.equals("production", ignoreCase = true))
                        CFSession.Environment.PRODUCTION
                    else CFSession.Environment.SANDBOX
                )
                .setPaymentSessionID(paymentSessionId)
                .setOrderId(orderId)
                .build()
            val payment = CFDropCheckoutPaymentBuilder()
                .setSession(cfSession)
                .build()
            CFPaymentGatewayService.getInstance().doPayment(this, payment)
        } catch (e: CFException) {
            Toast.makeText(this, e.message ?: "Unable to start Cashfree checkout.", Toast.LENGTH_LONG).show()
            setResult(RESULT_CANCELED)
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to start payment.", Toast.LENGTH_LONG).show()
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    override fun onPaymentVerify(orderID: String) {
        setResult(RESULT_OK, intent.putExtra(EXTRA_ORDER_ID, orderID).putExtra(EXTRA_RESULT, RESULT_VERIFY))
        finish()
    }

    override fun onPaymentFailure(cfErrorResponse: CFErrorResponse, orderID: String) {
        setResult(RESULT_CANCELED, intent.putExtra(EXTRA_ORDER_ID, orderID).putExtra(EXTRA_RESULT, RESULT_FAILURE))
        finish()
    }

    companion object {
        const val EXTRA_ORDER_ID = "cashfree_order_id"
        const val EXTRA_PAYMENT_SESSION_ID = "cashfree_payment_session_id"
        const val EXTRA_ENVIRONMENT = "cashfree_environment"
        const val EXTRA_RESULT = "cashfree_result"
        const val RESULT_VERIFY = "verify"
        const val RESULT_FAILURE = "failure"
    }
}
