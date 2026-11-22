package com.arnit.imagex.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Payment-provider selection UI.
 *
 * This is presentation-only. It intentionally does not initiate payment until
 * the app has a verified identity, a configured Google Play Billing client,
 * and a secure Cashfree backend checkout implementation.
 */
enum class PremiumPaymentMethod(val title: String, val subtitle: String) {
    GOOGLE_PLAY("Google Play", "For Play Store installs"),
    CASHFREE("Cashfree", "For direct APK purchases")
}

@Composable
fun PremiumPaymentMethodSelector(
    selected: PremiumPaymentMethod,
    onSelect: (PremiumPaymentMethod) -> Unit,
    googlePlayAvailable: Boolean,
    cashfreeAvailable: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Payment method", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        PremiumPaymentMethod.entries.forEach { method ->
            val available = when (method) {
                PremiumPaymentMethod.GOOGLE_PLAY -> googlePlayAvailable
                PremiumPaymentMethod.CASHFREE -> cashfreeAvailable
            }
            Card(
                onClick = { if (available) onSelect(method) },
                enabled = available,
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (selected == method) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RadioButton(
                        selected = selected == method,
                        onClick = { if (available) onSelect(method) },
                        enabled = available
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(method.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                        Text(method.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!available) {
                            Text("Not configured yet", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}
