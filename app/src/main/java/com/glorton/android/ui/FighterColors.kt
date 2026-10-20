package com.glorton.android.ui

import androidx.compose.ui.graphics.Color

/**
 * Maps the original color names (Colors list from frame_42) to Compose colors.
 */
fun fighterColor(colr: String): Color = when (colr) {
    "red" -> Color(0xFFE53935)
    "blue" -> Color(0xFF1E88E5)
    "green" -> Color(0xFF43A047)
    "orange" -> Color(0xFFFB8C00)
    else -> Color(0xFFBDBDBD)
}
