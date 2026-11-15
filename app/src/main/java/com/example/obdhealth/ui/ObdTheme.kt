package com.example.obdhealth.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Набір кольорів однієї теми. */
class Palette(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val border: Color,
    val ink: Color,
    val muted: Color,
    val accent: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val good: Color,
    val goodSoft: Color,
    val warn: Color,
    val warnSoft: Color,
    val warnText: Color,
    val danger: Color,
    val dangerSoft: Color,
    val recording: Color,
    val onRecording: Color,
    val aiBg: Color,
    val aiBorder: Color,
    val aiTitle: Color,
    val infoText: Color,
    val segmentActive: Color,
)

val DarkPalette = Palette(
    bg = Color(0xFF0F1416),
    surface = Color(0xFF182024),
    surface2 = Color(0xFF202B31),
    border = Color(0xFF26323A),
    ink = Color(0xFFEAF0F2),
    muted = Color(0xFF9DAAB0),
    accent = Color(0xFF3CC8D3),
    accentSoft = Color(0xFF123A40),
    onAccent = Color(0xFF06292C),
    good = Color(0xFF6FE0B0),
    goodSoft = Color(0xFF123A2E),
    warn = Color(0xFFF2B661),
    warnSoft = Color(0xFF3D2B10),
    warnText = Color(0xFFF2C98A),
    danger = Color(0xFFFF8A80),
    dangerSoft = Color(0xFF3D1E1C),
    recording = Color(0xFFFF6B5E),
    onRecording = Color(0xFF1A0B09),
    aiBg = Color(0xFF14282C),
    aiBorder = Color(0xFF23444A),
    aiTitle = Color(0xFF7FD8E0),
    infoText = Color(0xFFC5D0D5),
    segmentActive = Color(0xFF33434B),
)

val LightPalette = Palette(
    bg = Color(0xFFF3F4F1),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFECEEE9),
    border = Color(0xFFE2E4DE),
    ink = Color(0xFF151A1C),
    muted = Color(0xFF5B6468),
    accent = Color(0xFF0E7C86),
    accentSoft = Color(0xFFDDF0F1),
    onAccent = Color(0xFFFFFFFF),
    good = Color(0xFF1F7A3E),
    goodSoft = Color(0xFFE1F2E6),
    warn = Color(0xFF8A5000),
    warnSoft = Color(0xFFFBEBCF),
    warnText = Color(0xFF6B3D00),
    danger = Color(0xFFB3261E),
    dangerSoft = Color(0xFFFBE4E2),
    recording = Color(0xFFC62828),
    onRecording = Color(0xFFFFFFFF),
    aiBg = Color(0xFFEAF5F6),
    aiBorder = Color(0xFFCFE5E7),
    aiTitle = Color(0xFF0A5F67),
    infoText = Color(0xFF3A4549),
    segmentActive = Color(0xFFFFFFFF),
)

val LocalPalette = staticCompositionLocalOf { DarkPalette }

/**
 * Поточні кольори. Звертатися як Obd.surface, Obd.ink тощо (лише з @Composable-коду):
 * значення залежать від того, світла чи темна тема активна.
 */
object Obd {
    val bg: Color @Composable get() = LocalPalette.current.bg
    val surface: Color @Composable get() = LocalPalette.current.surface
    val surface2: Color @Composable get() = LocalPalette.current.surface2
    val border: Color @Composable get() = LocalPalette.current.border
    val ink: Color @Composable get() = LocalPalette.current.ink
    val muted: Color @Composable get() = LocalPalette.current.muted
    val accent: Color @Composable get() = LocalPalette.current.accent
    val accentSoft: Color @Composable get() = LocalPalette.current.accentSoft
    val onAccent: Color @Composable get() = LocalPalette.current.onAccent
    val good: Color @Composable get() = LocalPalette.current.good
    val goodSoft: Color @Composable get() = LocalPalette.current.goodSoft
    val warn: Color @Composable get() = LocalPalette.current.warn
    val warnSoft: Color @Composable get() = LocalPalette.current.warnSoft
    val warnText: Color @Composable get() = LocalPalette.current.warnText
    val danger: Color @Composable get() = LocalPalette.current.danger
    val dangerSoft: Color @Composable get() = LocalPalette.current.dangerSoft
    val recording: Color @Composable get() = LocalPalette.current.recording
    val onRecording: Color @Composable get() = LocalPalette.current.onRecording
    val aiBg: Color @Composable get() = LocalPalette.current.aiBg
    val aiBorder: Color @Composable get() = LocalPalette.current.aiBorder
    val aiTitle: Color @Composable get() = LocalPalette.current.aiTitle
    val infoText: Color @Composable get() = LocalPalette.current.infoText
    val segmentActive: Color @Composable get() = LocalPalette.current.segmentActive
}

@Composable
fun ObdTheme(dark: Boolean, content: @Composable () -> Unit) {
    val p = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) {
        darkColorScheme(
            primary = p.accent, onPrimary = p.onAccent,
            background = p.bg, onBackground = p.ink,
            surface = p.surface, onSurface = p.ink,
        )
    } else {
        lightColorScheme(
            primary = p.accent, onPrimary = p.onAccent,
            background = p.bg, onBackground = p.ink,
            surface = p.surface, onSurface = p.ink,
        )
    }
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
