package com.arnit.imagex.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.OutlinedTextField
import com.arnit.imagex.payment.CashfreeCheckoutActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

private val PremiumGold = Color(0xFFFFD166)
private val PremiumPurple = Color(0xFF6C4CE4)
private val PremiumDark = Color(0xFF15132C)

@Composable
fun PremiumScreen(vm: AppViewModel, onBack: () -> Unit) {
    val isPremium by vm.isPremium.collectAsState()
    val billingState by vm.billingState.collectAsState()
    var selectedPayment by remember { mutableStateOf(PremiumPaymentMethod.CASHFREE) }
    var phoneNumber by remember { mutableStateOf("") }
    val context = LocalContext.current
    val cashfreeOrder by vm.cashfreeOrder.collectAsState()
    val cashfreeBusy by vm.cashfreeBusy.collectAsState()
    val cashfreeMessage by vm.cashfreeMessage.collectAsState()
    val checkoutLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        val orderId = data?.getStringExtra(CashfreeCheckoutActivity.EXTRA_ORDER_ID).orEmpty()
        val status = data?.getStringExtra(CashfreeCheckoutActivity.EXTRA_RESULT).orEmpty()
        // Clear the pending order for every checkout result to prevent stale checkout relaunch.
        vm.clearCashfreeOrder()
        if (status == CashfreeCheckoutActivity.RESULT_VERIFY && orderId.isNotBlank()) {
            vm.verifyCashfreePayment(orderId)
        }
    }
    androidx.compose.runtime.LaunchedEffect(cashfreeOrder) {
        cashfreeOrder?.let { order ->
            val intent = Intent(context, CashfreeCheckoutActivity::class.java)
                .putExtra(CashfreeCheckoutActivity.EXTRA_ORDER_ID, order.orderId)
                .putExtra(CashfreeCheckoutActivity.EXTRA_SESSION_ID, order.paymentSessionId)
            checkoutLauncher.launch(intent)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Premium Membership", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        PremiumHero(isPremium = isPremium, testMode = billingState.testMode)

        if (isPremium) {
            SectionCard(title = "Membership status") {
                Text(
                    when (billingState.plan) {
                        PlanId.Monthly -> "Monthly membership is active."
                        PlanId.Yearly -> "Yearly membership is active."
                        PlanId.Lifetime -> "Lifetime membership is active."
                        else -> "Premium access is unlocked."
                    } + if (billingState.testMode) " • TEST MODE (not a real purchase)" else "",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedButton(onClick = { vm.restorePurchases() }) { Text("Restore / Refresh Access") }
            }
        } else {
            SectionCard(title = "Free plan") {
                Text(
                    "Convert JPG/JPEG, PNG, WebP and BMP offline. Save and share your results with no artificial quality reduction.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            SectionCard(title = "Unlock with Premium") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PremiumFeature.entries.forEach { feature -> PremiumRow(feature.title, feature.description) }
                }
            }

            SectionCard(title = "Choose your plan") {
                Text(
                    "Choose a plan. Checkout is handled securely by Cashfree and Premium unlocks only after server verification.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    billingState.products.forEach { product ->
                        val price = when (product.plan) {
                            PlanId.Monthly -> "₹99 / 30 days"
                            PlanId.Yearly -> "₹799 / year"
                            PlanId.Lifetime -> "₹1,499 lifetime"
                            else -> product.priceText
                        }
                        PlanCard(
                            planName = product.plan.displayName,
                            priceText = price,
                            description = product.description
                        )
                    }
                }
            }

            SectionCard(title = "Checkout") {
                PremiumPaymentMethodSelector(
                    selected = selectedPayment,
                    onSelect = { selectedPayment = it },
                    googlePlayAvailable = false,
                    cashfreeAvailable = true
                )
                OutlinedTextField(value = phoneNumber, onValueChange = { phoneNumber = it }, label = { Text("Phone number for payment") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Text("Cashfree Sandbox checkout. Payment is activated only after server verification.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                cashfreeMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                if (cashfreeBusy) Text("Processing…", style = MaterialTheme.typography.bodyMedium)
                billingState.products.forEach { product ->
                    GradientButton(text = "Pay with Cashfree • " + product.plan.displayName, onClick = { vm.createCashfreeOrder(product.plan, phoneNumber) }, enabled = !cashfreeBusy && selectedPayment == PremiumPaymentMethod.CASHFREE)
                }
                OutlinedButton(onClick = { vm.refreshServerPremiumStatus() }, enabled = !cashfreeBusy) { Text("Refresh membership status") }
            }

            SectionCard(title = "Free vs Premium limits") {
                Text(
                    "Maximum image dimension: Free ${FormatCatalog.FREE_MAX_DIMENSION}px • Premium ${FormatCatalog.PREMIUM_MAX_DIMENSION}px\n" +
                        "Batch queue: Free ${FormatCatalog.FREE_BATCH_LIMIT} • Premium ${FormatCatalog.PREMIUM_BATCH_LIMIT}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        billingState.message?.let { message ->
            if (billingState.testMode) WarningBanner(message) else InfoBanner(message)
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun PremiumHero(isPremium: Boolean, testMode: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = PremiumDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(Color(0xFF28204F), PremiumPurple, Color(0xFF32235D))))
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Star, contentDescription = null, tint = PremiumGold)
                Spacer(Modifier.width(8.dp))
                Text("ARNIT IMAGEX", color = PremiumGold, fontWeight = FontWeight.Bold)
            }
            Text(
                if (isPremium) "Premium unlocked" else "Upgrade your image workflow",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
            Text(
                if (isPremium) "Enjoy your Premium benefits." else "More power. Bigger images. Batch conversion.",
                color = Color.White.copy(alpha = 0.88f),
                style = MaterialTheme.typography.bodyMedium
            )
            if (testMode) {
                Text("TEST MODE • purchases are simulated", color = PremiumGold, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun PremiumRow(title: String, description: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = Color(0xFF2E7D32))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp))
        }
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 34.dp))
    }
}

@Composable
private fun PlanCard(planName: String, priceText: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(planName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(priceText, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
