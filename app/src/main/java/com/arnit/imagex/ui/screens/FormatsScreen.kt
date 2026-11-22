package com.arnit.imagex.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arnit.imagex.formats.FormatCatalog
import com.arnit.imagex.formats.FormatSupport
import com.arnit.imagex.ui.AppViewModel
import com.arnit.imagex.ui.components.InfoBanner
import com.arnit.imagex.ui.components.SectionCard

/**
 * Honest per-format availability. Everything shown here is derived from what
 * is actually registered at runtime - nothing is advertised that the build
 * cannot do.
 */
@Composable
fun FormatsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val isPremium by vm.isPremium.collectAsState()
    val supports = vm.formatSupports()

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
                "Supported Formats",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        InfoBanner(
            "This list reflects exactly what this build and this device can do. " +
                "Nothing is advertised as supported unless a working codec is present."
        )

        val total = supports.size
        val decodable = supports.count { it.canDecode }
        val encodable = supports.count { it.canEncode }
        SectionCard(title = "Overview") {
            Text(
                "$decodable of $total known formats can be decoded, $encodable can be encoded on this device.",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "Free tier: JPG/JPEG, PNG, WebP, BMP in and out." +
                    if (isPremium) " Premium is active." else
                        " Premium unlocks the advanced formats, custom quality and larger images.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Size limit: ${FormatCatalog.FREE_MAX_DIMENSION}px on Free, " +
                    "${FormatCatalog.PREMIUM_MAX_DIMENSION}px with Premium.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        supports.forEach { support ->
            FormatRow(support)
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FormatRow(support: FormatSupport) {
    val format = support.format
    SectionCard(title = format.displayName) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                support.statusLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (support.available) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            CapabilityRow("Decode", support.canDecode)
            CapabilityRow("Encode", support.canEncode)
            if (support.encodeLosslessPossible) {
                CapabilityRow("Lossless output", true)
            }
            if (format.inherentlyLossy) {
                Text(
                    "This format is inherently lossy; pixels cannot be stored losslessly.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (support.premiumOnlyDecode) {
                Text(
                    "Premium input format",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            if (support.premiumOnlyEncode) {
                Text(
                    "Premium output format",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            if (format.description.isNotBlank()) {
                Text(
                    format.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            support.note?.let { note ->
                Text(
                    note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CapabilityRow(label: String, enabled: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (enabled) Icons.Filled.Check else Icons.Filled.Close,
            contentDescription = null,
            tint = if (enabled) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
            modifier = Modifier.height(16.dp)
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}
