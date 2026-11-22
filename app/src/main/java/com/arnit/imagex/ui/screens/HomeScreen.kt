package com.arnit.imagex.ui.screens

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
import androidx.compose.material.icons.filled.Person
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
    onSettings: () -> Unit,
    onAccount: () -> Unit
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
        HeroCard(isTestMode = billingState.testMode, onConvert = onConvert)
        HomeTile(Icons.Filled.Image, "Convert Image", "Pick one image and choose any output format", onConvert)
        HomeTile(Icons.Filled.StackedLineChart, "Batch Convert", if (isPremium) "Queue up to 50 images" else "Premium feature", onBatch)
        HomeTile(Icons.Filled.History, "Recent Conversions", "Metadata only - never image contents", onHistory)
        HomeTile(Icons.Filled.Shield, "Supported Formats", "See what this build can decode and encode", onFormats)
        if (!isPremium) {
            HomeTile(Icons.Filled.AutoAwesome, "Premium Upgrade", "Batch mode, advanced formats, larger images", true, onPremium)
        }
        HomeTile(Icons.Filled.Person, "Account", "Sign in or create an ImageX account", onAccount)
        HomeTile(Icons.Filled.Settings, "Settings", "Theme, defaults, privacy and cleanup", onSettings)
        Spacer(Modifier.height(30.dp))
        Text("Images are processed fully offline on this device and never uploaded.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HeroCard(isTestMode: Boolean, onConvert: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().background(
            brush = Brush.linearGradient(listOf(BrandGradientStart, BrandGradientEnd)),
            shape = RoundedCornerShape(26.dp)
        ).clickable { onConvert() }.padding(24.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).background(Color.White.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Image, contentDescription = null, tint = Color.White)
                }
                Text(if (isTestMode) "Premium (TEST MODE)" else "Universal Image Converter",
                    style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.padding(start = 12.dp))
            }
            Text("Convert anything to PNG, JPEG, WebP, TIFF and more -\noriginal resolution, top quality, 100% offline.",
                style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.92f), modifier = Modifier.padding(top = 14.dp))
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
            Icon(icon, contentDescription = null,
                tint = if (highlighted) com.arnit.imagex.ui.theme.Purple500 else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(start = 12.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
