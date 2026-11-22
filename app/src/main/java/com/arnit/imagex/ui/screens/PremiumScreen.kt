package com.arnit.imagex.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arnit.imagex.billing.BillingState
import com.arnit.imagex.billing.PlanId
import com.arnit.imagex.formats.FormatCatalog
import com.arnit.imagex.premium.PremiumFeature
import com.arnit.imagex.ui.AppViewModel
import com.arnit.imagex.ui.components.GradientButton
import com.arnit.imagex.ui.components.InfoBanner
import com.arnit.imagex.ui.components.SectionCard
import com.arnit.imagex.ui.components.WarningBanner
import com.arnit.imagex.ui.components.PremiumPaymentMethod
import com.arnit.imagex.ui.components.PremiumPaymentMethodSelector
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
import android.content.Intent
import com.google.firebase.auth.FirebaseAuth
import com.arnit.imagex.BuildConfig
import com.arnit.imagex.billing.cashfree.CashfreeBackendClient
import com.arnit.imagex.billing.cashfree.CashfreeCheckoutActivity
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch

@Composable
fun PremiumScreen(vm: AppViewModel, onBack: () -> Unit) {
    val isPremium by vm.isPremium.collectAsState()
    val billingState by vm.billingState.collectAsState()
    var selectedPayment by remember { mutableStateOf(PremiumPaymentMethod.GOOGLE_PLAY) }
    var cashfreeMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cashfreeConfigured = BuildConfig.CASHFREE_BACKEND_URL.isNotBlank()
    val cashfreeClient = remember { CashfreeBackendClient(BuildConfig.CASHFREE_BACKEND_URL) }
    val checkoutLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val orderId = result.data?.getStringExtra(CashfreeCheckoutActivity.EXTRA_ORDER_ID)
        if (result.resultCode == Activity.RESULT_OK && !orderId.isNullOrBlank()) {
            scope.launch {
                cashfreeMessage = "Checking payment securely..."
                try {
                    val verified = cashfreeClient.verifyOrder(orderId)
                    if (verified.premiumUnlocked) {
                        vm.activateVerifiedCashfreePremium(verified.plan)
                        cashfreeMessage = "Payment verified by server. Premium activated for this session."
                    } else {
                        cashfreeMessage = "Payment status: ${verified.status}. Premium is not activated yet."
                    }
                } catch (e: Exception) {
                    cashfreeMessage = e.message ?: "Payment verification could not be completed. Premium remains locked."
                }
            }
        } else if (result.resultCode != Activity.RESULT_OK) {
            cashfreeMessage = "Payment was cancelled or failed. Premium remains locked."
        }
    }
    val startCashfree: (PlanId) -> Unit = { plan ->
        scope.launch {
            if (!cashfreeConfigured) {
                cashfreeMessage = "Cashfree backend URL is not configured in this build."
            } else if (FirebaseAuth.getInstance().currentUser == null) {
                cashfreeMessage = "Please sign in before purchasing Premium."
            } else {
                cashfreeMessage = "Creating secure payment order..."
                try {
                    val planKey = when (plan) {
                        PlanId.Monthly -> "monthly"
                        PlanId.Yearly -> "yearly"
                        PlanId.Lifetime -> "lifetime"
                        else -> throw IllegalArgumentException("Invalid Premium plan.")
                    }
                    val order = cashfreeClient.createOrder(planKey)
                    val intent = Intent(context, CashfreeCheckoutActivity::class.java).apply {
                        putExtra(CashfreeCheckoutActivity.EXTRA_ORDER_ID, order.orderId)
                        putExtra(CashfreeCheckoutActivity.EXTRA_PAYMENT_SESSION_ID, order.paymentSessionId)
                        putExtra(CashfreeCheckoutActivity.EXTRA_ENVIRONMENT, order.environment)
                    }
                    checkoutLauncher.launch(intent)
                } catch (e: Exception) {
                    cashfreeMessage = e.message ?: "Unable to create payment order."
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                "Premium",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        if (isPremium) {
            SectionCard(title = "Premium active") {
                Text(
                    when (val plan = billingState.plan) {
                        PlanId.Monthly -> "Monthly plan active."
                        PlanId.Yearly -> "Yearly plan active."
                        PlanId.Lifetime -> "Lifetime plan active."
                        else -> "Premium unlocked."
                    } + if (billingState.testMode) " (TEST MODE - not a real purchase)" else "",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedButton(onClick = { vm.restorePurchases() }) { Text("Restore Purchases") }
            }
        } else {
            SectionCard(title = "Free") {
                Text(
                    "Basic conversion between JPG/JPEG, PNG, WebP and BMP with full quality, " +
                        "the Photo Picker, save and share - completely offline and without " +
                        "artificially reduced quality.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            SectionCard(title = "Premium features") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PremiumFeature.entries.forEach { feature ->
                        PremiumRow(feature.title, feature.description)
                    }
                }
            }

            SectionCard(title = "Limits") {
                Text(
                    "Images up to ${FormatCatalog.FREE_MAX_DIMENSION}px on Free and " +
                        "${FormatCatalog.PREMIUM_MAX_DIMENSION}px with Premium; " +
                        "batch queue of ${FormatCatalog.FREE_BATCH_LIMIT} on Free and " +
                        "${FormatCatalog.PREMIUM_BATCH_LIMIT} with Premium.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            SectionCard(title = "Payment options") {
                PremiumPaymentMethodSelector(
                    selected = selectedPayment,
                    onSelect = { selectedPayment = it },
                    googlePlayAvailable = false,
                    cashfreeAvailable = cashfreeConfigured
                )
                Text(
                    "Google Play Billing and Cashfree checkout are not connected yet. Payment options are shown for the upcoming integration.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            PlansSection(billingState, vm, selectedPayment == PremiumPaymentMethod.CASHFREE, cashfreeConfigured, startCashfree)
        }

        cashfreeMessage?.let { message -> InfoBanner(message) }

        billingState.message?.let { message ->
            if (billingState.testMode) {
                WarningBanner(message)
            } else {
                InfoBanner(message)
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PremiumRow(title: String, description: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = Color(0xFF2E7D32)
            )
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 10.dp)
            )
        }
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 34.dp)
        )
    }
}

@Composable
private fun PlansSection(billingState: BillingState, vm: AppViewModel, showCashfree: Boolean, cashfreeEnabled: Boolean, onCashfreePay: (PlanId) -> Unit) {
    SectionCard(title = "Plans") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            billingState.products.forEach { product ->
                PlanCard(
                    planName = product.plan.displayName,
                    priceText = product.priceText,
                    description = product.description,
                    enabled = billingState.testMode,
                    onBuy = { vm.purchase(product.plan) }
                )
                if (showCashfree) {
                    OutlinedButton(onClick = { onCashfreePay(product.plan) }, enabled = cashfreeEnabled, modifier = Modifier.fillMaxWidth()) {
                        Text("Pay with Cashfree")
                    }
                }
            }
            if (!billingState.testMode) {
                WarningBanner(
                    "Payment is not configured for this build. Purchase buttons are disabled; " +
                        "the test-mode toggle lives in Settings and never fakes a real purchase."
                )
            }
            OutlinedButton(
                onClick = { vm.restorePurchases() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Restore Purchases") }
        }
    }
}

@Composable
private fun PlanCard(
    planName: String,
    priceText: String,
    description: String,
    enabled: Boolean,
    onBuy: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                planName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                priceText,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        GradientButton(
            text = if (enabled) "Get $planName" else "Unavailable",
            onClick = onBuy,
            enabled = enabled
        )
    }
}
