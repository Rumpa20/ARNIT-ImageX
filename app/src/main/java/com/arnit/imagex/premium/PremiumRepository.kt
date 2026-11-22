package com.arnit.imagex.premium

import com.arnit.imagex.billing.BillingGateway
import com.arnit.imagex.billing.PlanId
import com.arnit.imagex.core.QualityMode
import com.arnit.imagex.formats.FormatCatalog
import com.arnit.imagex.formats.ImageFormatId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Feature catalogue + entitlement resolution. Gating is centralized here so
 * screens only ask "is this allowed?" instead of duplicating rules.
 */
enum class PremiumFeature(val title: String, val description: String) {
    BATCH("Batch conversion", "Queue many images and convert them in one run."),
    ADVANCED_FORMATS("Advanced formats", "Decode and encode TIFF, TGA, ICO, ICNS, DDS, PSD, WBMP, PNM, HEIC and AVIF."),
    TIFF_RAW("TIFF & advanced pipelines", "Baseline TIFF conversion with strip-based decoding."),
    RAW("Camera RAW decoding", "RAW conversion where a device decoder is available."),
    ADVANCED_QUALITY("Advanced quality control", "Custom quality slider and premium presets."),
    CUSTOM_COMPRESSION("Custom compression", "Fine-grained size tuning per conversion."),
    ADVANCED_RESIZE("Advanced resizing", "Upscaling, independent dimensions and aspect-free resize."),
    LARGER_IMAGES("Larger images", "Decode and convert images up to ${FormatCatalog.PREMIUM_MAX_DIMENSION}px."),
    NO_ADS("No advertisements", "The app contains no ads today; Premium keeps it that way."),
    PRESETS("Premium presets", "One-tap profiles tuned for web, photo and archival use.")
}

data class FeatureLimits(
    val maxDimension: Int,
    val batchLimit: Int,
    val customQualityAllowed: Boolean,
    val advancedResizeAllowed: Boolean
)

class PremiumRepository(
    private val billing: BillingGateway,
    scope: CoroutineScope
) {
    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium

    private val _testMode = MutableStateFlow(false)
    val testMode: StateFlow<Boolean> = _testMode

    private val _activePlan = MutableStateFlow<PlanId?>(null)
    val activePlan: StateFlow<PlanId?> = _activePlan

    init {
        scope.launch {
            billing.state.collect { state ->
                _isPremium.value = state.unlocked
                _activePlan.value = state.plan
                _testMode.value = state.testMode
            }
        }
    }

    fun limits(): FeatureLimits = if (_isPremium.value) {
        FeatureLimits(
            maxDimension = FormatCatalog.PREMIUM_MAX_DIMENSION,
            batchLimit = FormatCatalog.PREMIUM_BATCH_LIMIT,
            customQualityAllowed = true,
            advancedResizeAllowed = true
        )
    } else {
        FeatureLimits(
            maxDimension = FormatCatalog.FREE_MAX_DIMENSION,
            batchLimit = FormatCatalog.FREE_BATCH_LIMIT,
            customQualityAllowed = false,
            advancedResizeAllowed = false
        )
    }

    fun canUse(feature: PremiumFeature): Boolean = when (feature) {
        PremiumFeature.NO_ADS -> true
        else -> _isPremium.value
    }

    fun inputAllowed(format: ImageFormatId): Boolean =
        _isPremium.value || format in FormatCatalog.FREE_INPUT

    fun outputAllowed(format: ImageFormatId): Boolean =
        _isPremium.value || format in FormatCatalog.FREE_OUTPUT

    fun qualityAllowed(mode: QualityMode): Boolean =
        _isPremium.value || mode != QualityMode.CUSTOM
}
