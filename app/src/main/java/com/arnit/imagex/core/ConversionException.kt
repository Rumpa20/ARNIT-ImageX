package com.arnit.imagex.core

/**
 * Thrown when a conversion step fails. Every failure path inside the app is
 * mapped to one of these codes so the UI can show a clear, honest message
 * instead of crashing.
 */
enum class ErrorCode {
    UNSUPPORTED_FORMAT,
    CORRUPTED_IMAGE,
    INVALID_EXTENSION,
    DECODER_FAILED,
    ENCODER_FAILED,
    ENCODER_UNAVAILABLE,
    OUT_OF_MEMORY,
    IMAGE_TOO_LARGE,
    INSUFFICIENT_STORAGE,
    CANCELLED,
    METADATA_UNSUPPORTED,
    ANIMATED_LIMITATION,
    IO_ERROR
}

class ConversionException(
    val code: ErrorCode,
    override val message: String,
    val hint: String? = null,
    cause: Throwable? = null
) : Exception(message, cause)

object ErrorMessages {

    fun forCode(code: ErrorCode, detail: String? = null): String = when (code) {
        ErrorCode.UNSUPPORTED_FORMAT ->
            "This file format is not supported by the app on this device." +
                (detail?.let { " ($it)" } ?: "")
        ErrorCode.CORRUPTED_IMAGE ->
            "The image appears to be corrupted or is not a valid image file." +
                (detail?.let { " ($it)" } ?: "")
        ErrorCode.INVALID_EXTENSION ->
            "The file extension does not match the actual file contents. The real format was detected where possible."
        ErrorCode.DECODER_FAILED ->
            "The image could not be decoded. It may use an unsupported variant of this format." +
                (detail?.let { " ($it)" } ?: "")
        ErrorCode.ENCODER_FAILED ->
            "The image could not be encoded in the selected output format." +
                (detail?.let { " ($it)" } ?: "")
        ErrorCode.ENCODER_UNAVAILABLE ->
            "This device does not provide an encoder for the selected output format."
        ErrorCode.OUT_OF_MEMORY ->
            "Running out of memory. The image is too large for this device. Try a smaller resize or use Premium limits."
        ErrorCode.IMAGE_TOO_LARGE ->
            "The image exceeds the size limit for the current plan." +
                (detail?.let { " ($it)" } ?: "")
        ErrorCode.INSUFFICIENT_STORAGE ->
            "There is not enough free storage to write the converted file."
        ErrorCode.CANCELLED ->
            "The conversion was cancelled."
        ErrorCode.METADATA_UNSUPPORTED ->
            "Some metadata of the source image is not supported by the output format and was skipped."
        ErrorCode.ANIMATED_LIMITATION ->
            "Animated images are processed using their first frame in this conversion."
        ErrorCode.IO_ERROR ->
            "The file could not be read or written." + (detail?.let { " ($it)" } ?: "")
    }
}
