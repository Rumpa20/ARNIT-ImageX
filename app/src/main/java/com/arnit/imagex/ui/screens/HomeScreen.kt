package com.arnit.imagex.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.StackedLineChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import com.arnit.imagex.R
import androidx.compose.ui.unit.dp
import com.arnit.imagex.ui.AppViewModel
import com.arnit.imagex.ui.components.SectionCard
import com.arnit.imagex.ui.theme.BrandGradientEnd
import com.arnit.imagex.ui.theme.BrandGradientStart

@Composable
fun HomeScreen(
    vm: AppViewModel,
    onConvert: () -> Unit,
    onBatch: () -> Unit,
    onHistory: () -> Unit,
    onFormats: () -> Unit,
    onPremium: () -> Unit,
    onSettings: () -> Unit
) {
    val isPremium by vm.isPremium.collectAsState()
    val billingState by vm.billingState.collectAsState()
    val background = MaterialTheme.colorScheme.background

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(26.dp))
        HeroCard(
            isTestMode = billingState.testMode,
            onConvert = onConvert
        )

        HomeTile(
            icon = Icons.Filled.Image,
            title = "Convert Image",
            subtitle = "Pick one image and choose any output format",
            onClick = onConvert
        )
        HomeTile(
            icon = Icons.Filled.StackedLineChart,
            title = "Batch Convert",
            subtitle = if (isPremium) "Queue up to 50 images" else "Premium feature",
            onClick = onBatch
        )
        HomeTile(
            icon = Icons.Filled.History,
            title = "Recent Conversions",
            subtitle = "Metadata only - never image contents",
            onClick = onHistory
        )
        HomeTile(
            icon = Icons.Filled.Shield,
            title = "Supported Formats",
            subtitle = "See what this build can decode and encode",
            onClick = onFormats
        )
        if (!isPremium) {
            HomeTile(
                icon = Icons.Filled.AutoAwesome,
                title = "Premium Upgrade",
                subtitle = "Batch mode, advanced formats, larger images",
                highlighted = true,
                onClick = onPremium
            )
        }
        HomeTile(
            icon = Icons.Filled.Settings,
            title = "Settings",
            subtitle = "Theme, defaults, privacy and cleanup",
            onClick = onSettings
        )
        Spacer(Modifier.height(30.dp))
        Text(
            "Images are processed fully offline on this device and never uploaded.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HeroCard(isTestMode: Boolean, onConvert: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.linearGradient(listOf(BrandGradientStart, BrandGradientEnd)),
                shape = RoundedCornerShape(26.dp)
            )
            .clickable { onConvert() }
            .padding(24.dp)
    ) {
        Column {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(Color.White.copy(alpha = 0.18f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_imagex_logo),
                        contentDescription = "ARNIT ImageX logo",
                        modifier = Modifier.size(92.dp),
                        contentScale = ContentScale.Fit
                    )
                }
                Text(
                    if (isTestMode) "Premium (TEST MODE)" else "Universal Image Converter",
                    style = MaterialTheme.typography.titleMedium,
                    color = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.padding(top = 12.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            Text(
                "Convert anything to PNG, JPEG, WebP, TIFF and more -\noriginal resolution, top quality, 100% offline.",
                style = MaterialTheme.typography.bodyLarge,
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.92f),
                modifier = Modifier.padding(top = 14.dp)
            )
        }
    }
}

@Composable
private fun HomeTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    highlighted: Boolean = false,
    onClick: () -> Unit
) {
    SectionCard(title = title, modifier = Modifier.clickable { onClick() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (highlighted) com.arnit.imagex.ui.theme.Purple500 else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp)
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            )
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
