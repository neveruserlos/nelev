package com.nelev.dev.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

// ─── Theme colors ─────────────────────────────────────────────────────────────

@Composable
fun nelevColors(isDark: Boolean, effectsEnabled: Boolean = true): Map<String, Color> {
    val t by animateFloatAsState(
        targetValue = if (isDark) 1f else 0f,
        animationSpec = tween(if (effectsEnabled) 180 else 0, easing = FastOutSlowInEasing),
        label = "themeProgress"
    )
    fun lerp(a: Color, b: Color): Color = Color(
        a.red   + (b.red   - a.red)   * t,
        a.green + (b.green - a.green) * t,
        a.blue  + (b.blue  - a.blue)  * t,
        a.alpha + (b.alpha - a.alpha) * t
    )
    val lightBg    = Color(0xFFFFFBFF)
    val darkBg     = Color(0xFF050505)
    val lightCard  = Color(0xFFFFF7FC)
    val darkCard   = Color(0xFF101010)
    val lightBorder = Color(0xFFF1DEE9)
    val darkBorder = Color(0xFF242424)
    val lightText  = Color(0xFF8E4F6E)
    val darkText   = Color(0xFFF5F5F2)
    val lightMuted = Color(0xFFB88EA2)
    val darkMuted  = Color(0xFF6E6E6E)
    val lightNavBg = Color(0xFFFFF9FE)
    val darkNavBg  = Color(0xFF0C0C0C)
    val lightConnBase   = Color(0xFFBBBBBB)
    val darkConnBase    = Color(0xFF555555)
    val lightConnActive = Color(0xFFE79BC0)
    val darkConnActive  = Color(0xFFFFFFFF)
    val lightAccent     = Color(0xFFE79BC0)
    val darkAccent      = Color(0xFFF5F5F2)
    return remember(t) {
        mapOf(
            "bg" to lerp(lightBg, darkBg),
            "card" to lerp(lightCard, darkCard),
            "border" to lerp(lightBorder, darkBorder),
            "text" to lerp(lightText, darkText),
            "muted" to lerp(lightMuted, darkMuted),
            "navBg" to lerp(lightNavBg, darkNavBg),
            "connBase" to lerp(lightConnBase, darkConnBase),
            "connActive" to lerp(lightConnActive, darkConnActive),
            "accent" to lerp(lightAccent, darkAccent)
        )
    }
}
