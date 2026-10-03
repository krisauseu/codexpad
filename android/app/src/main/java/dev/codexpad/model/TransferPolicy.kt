package dev.codexpad.model

/** Shared by the composer, upload validation and HTTP client. Counts Unicode code points. */
object TransferPolicy {
    const val MAX_MESSAGE = 12_000
    const val MESSAGE_WARNING = 10_250 // Same remaining proportion as the previous 3500 / 4096 threshold.
    const val MESSAGE_LIMIT_ERROR = "Nachricht zu lang. Maximal 12000 Zeichen sind erlaubt."
    val textTypes = mapOf(".txt" to "text/plain", ".md" to "text/markdown",
        ".html" to "text/html", ".htm" to "text/html")
    val pickerMimeTypes = arrayOf("image/png", "image/jpeg", "image/webp", *textTypes.values.distinct().toTypedArray())

    fun messageLength(message: String) = message.codePointCount(0, message.length)
    fun acceptsText(name: String, mime: String?) =
        textTypes.keys.any { name.endsWith(it, ignoreCase = true) } && mime in textTypes.values

    // Offer source files to external text editors; saving retains the original MIME type.
    fun openMimeType(mime: String) = if (mime in setOf("text/markdown", "text/html")) "text/plain" else mime
}
