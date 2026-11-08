package com.axiomstead.foldsms

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The app's two colour schemes. Text colours are chosen so that every text and background pair used in the app has a contrast
 * ratio of at least 4.5:1 (WCAG AA); `ContrastTest` checks it, so a later colour change cannot quietly break it.
 */
internal val darkAppColors = darkColorScheme(
    primary = Color(0xFFF2A26A), onPrimary = Color(0xFF112022),
    secondary = Color(0xFF6DC3B8), background = Color(0xFF051214),
    surface = Color(0xFF112022), surfaceVariant = Color(0xFF1B292C),
    onSurface = Color(0xFFD9ECEF), onSurfaceVariant = Color(0xFF9DAFB2),
    outline = Color(0xFF314043), outlineVariant = Color(0xFF253336)
)

internal val lightAppColors = lightColorScheme(
    primary = Color(0xFF9A4D00), onPrimary = Color(0xFFFDF8EF),
    secondary = Color(0xFF00756B), background = Color(0xFFF4ECE0),
    surface = Color(0xFFFDF8EF), surfaceVariant = Color(0xFFEBDCC9),
    onSurface = Color(0xFF211C15), onSurfaceVariant = Color(0xFF6B6050),
    outline = Color(0xFFD6C8B4), outlineVariant = Color(0xFFE2D3BF)
)

/** WCAG 2.x contrast ratio between two opaque colours. */
internal fun contrastRatio(a: Color, b: Color): Double {
    fun channel(value: Float): Double = value.toDouble().let { if (it <= 0.03928) it / 12.92 else Math.pow((it + 0.055) / 1.055, 2.4) }
    fun luminance(c: Color) = 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    val l1 = luminance(a); val l2 = luminance(b)
    return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
}
