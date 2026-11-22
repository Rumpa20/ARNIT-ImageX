package com.arnit.imagex.core

enum class QualityMode(val label: String, val defaultQuality: Int) {
    ORIGINAL("Original Quality", 100),
    LOSSLESS("Lossless", 100),
    HIGH("High Quality", 92),
    BALANCED("Balanced", 80),
    SMALLER("Smaller File", 60),
    CUSTOM("Custom", 90);
}

/**
 * Fully resolved settings for one conversion.
 * The user always explicitly opts in to resizing; it is never automatic.
 */
data class ConversionSettings(
    val outputFormatId: String,
    val qualityMode: QualityMode = QualityMode.HIGH,
    val customQuality: Int = 92,
    val resizeEnabled: Boolean = false,
    val resizeWidth: Int = 0,
    val resizeHeight: Int = 0,
    val keepAspect: Boolean = true,
    val allowOversizedDownscale: Boolean = false,
    val preserveMetadata: Boolean = false
)

data class ConversionProgress(
    val stage: Stage,
    val percent: Int,
    val detail: String? = null
) {
    enum class Stage { COPYING_SOURCE, DECODING, TRANSFORMING, ENCODING, DONE }
}

fun interface ProgressListener {
    fun onProgress(progress: ConversionProgress)
}
