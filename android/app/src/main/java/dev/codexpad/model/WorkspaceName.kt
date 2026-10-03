package dev.codexpad.model

fun workspaceNameError(value: String): String? {
    val name = value.trim()
    val points = name.codePoints().toArray()
    return if (points.size !in 1..80 || name.toByteArray(Charsets.UTF_8).size > 200 ||
        points.firstOrNull()?.let { !Character.isLetterOrDigit(it) } != false ||
        name.endsWith(".") || ".." in name ||
        points.any { !Character.isLetterOrDigit(it) && it !in " _-.".map(Char::code) })
        "1–80 Zeichen: Buchstaben, Ziffern, Leerzeichen, _ - und einzelne Punkte. Mit Buchstabe oder Ziffer beginnen; kein Punkt am Ende."
    else null
}
