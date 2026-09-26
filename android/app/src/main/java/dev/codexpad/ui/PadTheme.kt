package dev.codexpad.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One quiet palette and spacing vocabulary for the existing tablet screens. */
@Composable
internal fun PadTheme(content: @Composable () -> Unit) {
    val base = Typography()
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF27675E), onPrimary = Color.White,
            primaryContainer = Color(0xFFDCEEE7), onPrimaryContainer = Color(0xFF163D36),
            secondary = Color(0xFF566960), secondaryContainer = Color(0xFFE8EFEA),
            onSecondaryContainer = Color(0xFF263B32),
            background = Color(0xFFF5F6F3), onBackground = Color(0xFF202A26),
            surface = Color(0xFFFCFDF9), onSurface = Color(0xFF202A26),
            surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF0F3EE),
            surfaceContainer = Color(0xFFEBEFE9), surfaceContainerHigh = Color(0xFFE4EAE3),
            surfaceContainerHighest = Color(0xFFDDE5DC), onSurfaceVariant = Color(0xFF58655E),
            outline = Color(0xFF78857D), outlineVariant = Color(0xFFD4DDD4),
            error = Color(0xFFA43C32), errorContainer = Color(0xFFFBE9E5), onErrorContainer = Color(0xFF6C271F)
        ),
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(24.dp)),
        typography = base.copy(
            titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            bodyLarge = base.bodyLarge.copy(fontSize = 17.sp, lineHeight = 27.sp),
            bodyMedium = base.bodyMedium.copy(lineHeight = 23.sp),
            labelSmall = base.labelSmall.copy(fontSize = 12.sp, lineHeight = 17.sp)
        ), content = content
    )
}

internal fun statusLabel(status: String): String = when (status) {
    "idle" -> "Bereit"
    "notLoaded" -> "Im Verlauf"
    "active", "inProgress" -> "In Arbeit"
    "completed" -> "Abgeschlossen"
    "interrupted" -> "Gestoppt"
    "failed", "systemError" -> "Fehler"
    else -> status
}

@Composable
internal fun StatusBadge(label: String, active: Boolean = false) {
    Surface(shape = MaterialTheme.shapes.small,
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) {
        Text(label, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
internal fun EmptyState(title: String, description: String) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
