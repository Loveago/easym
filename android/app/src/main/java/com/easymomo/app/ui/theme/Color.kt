package com.easymomo.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Brand Core — Original EasyMoMo Emerald & Gold Fintech Terminal Palette
val EmeraldPrimary = Color(0xFF0B6E4F)
val EmeraldPrimaryDark = Color(0xFF084C37)
val AmberAccent = Color(0xFFD97706)

// Network Brand Colors (Ghana)
val MtnYellow = Color(0xFFFFCC00)
val MtnOnYellow = Color(0xFF3A2E00)
val AtBlue = Color(0xFF0056B3)
val AtOnBlue = Color(0xFFFFFFFF)
val TelecelRed = Color(0xFFD90429)
val TelecelOnRed = Color(0xFFFFFFFF)

@Immutable
data class EasyMoMoPalette(
    val emeraldPrimaryContainer: Color,
    val onEmeraldPrimaryContainer: Color,
    val amberContainer: Color,
    val onAmberContainer: Color,
    val terminalBackground: Color,
    val terminalSurface: Color,
    val terminalSurfaceVariant: Color,
    val terminalBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val statusSuccess: Color,
    val statusSuccessBg: Color,
    val statusWarning: Color,
    val statusWarningBg: Color,
    val statusError: Color,
    val statusErrorBg: Color,
    val statusInfo: Color,
    val statusInfoBg: Color
)

val LightEasyMoMoPalette = EasyMoMoPalette(
    emeraldPrimaryContainer = Color(0xFFD8F3E8),
    onEmeraldPrimaryContainer = Color(0xFF052E20),
    amberContainer = Color(0xFFFEF3C7),
    onAmberContainer = Color(0xFF78350F),
    terminalBackground = Color(0xFFF5F7F6),
    terminalSurface = Color(0xFFFFFFFF),
    terminalSurfaceVariant = Color(0xFFEEF2F0),
    terminalBorder = Color(0xFFDDE3E0),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF52606D),
    textMuted = Color(0xFF8292A2),
    statusSuccess = Color(0xFF15803D),
    statusSuccessBg = Color(0xFFDCFCE7),
    statusWarning = Color(0xFFB45309),
    statusWarningBg = Color(0xFFFEF3C7),
    statusError = Color(0xFFB91C1C),
    statusErrorBg = Color(0xFFFEE2E2),
    statusInfo = Color(0xFF0369A1),
    statusInfoBg = Color(0xFFE0F2FE)
)

val DarkEasyMoMoPalette = EasyMoMoPalette(
    emeraldPrimaryContainer = Color(0xFF0E3B2B),
    onEmeraldPrimaryContainer = Color(0xFFA7F3D0),
    amberContainer = Color(0xFF3D2807),
    onAmberContainer = Color(0xFFFDE68A),
    terminalBackground = Color(0xFF0B1210),
    terminalSurface = Color(0xFF131D19),
    terminalSurfaceVariant = Color(0xFF1A2822),
    terminalBorder = Color(0xFF263830),
    textPrimary = Color(0xFFF1F5F9),
    textSecondary = Color(0xFF94A3B8),
    textMuted = Color(0xFF64748B),
    statusSuccess = Color(0xFF4ADE80),
    statusSuccessBg = Color(0xFF0F2E1E),
    statusWarning = Color(0xFFFBBF24),
    statusWarningBg = Color(0xFF332409),
    statusError = Color(0xFFF87171),
    statusErrorBg = Color(0xFF381618),
    statusInfo = Color(0xFF38BDF8),
    statusInfoBg = Color(0xFF0C2A3E)
)

val LocalEasyMoMoPalette = staticCompositionLocalOf { LightEasyMoMoPalette }

val EmeraldPrimaryContainer: Color
    @Composable @ReadOnlyComposable get() = LocalEasyMoMoPalette.current.emeraldPrimaryContainer

val OnEmeraldPrimaryContainer: Color
    @Composable @ReadOnlyComposable get() = LocalEasyMoMoPalette.current.onEmeraldPrimaryContainer

val AmberContainer: Color
    @Composable @ReadOnlyComposable get() = LocalEasyMoMoPalette.current.amberContainer

val OnAmberContainer: Color
    @Composable @ReadOnlyComposable get() = LocalEasyMoMoPalette.current.onAmberContainer

// Neutrals & Surfaces
val TerminalBackground: Color
    @Composable @ReadOnlyComposable get() = LocalEasyMoMoPalette.current.terminalBackground

val TerminalSurface: Color
    @Composable @ReadOnlyComposable get() = LocalEasyMoMoPalette.current.terminalSurface

val TerminalSurfaceVariant: Color
    @Composable @ReadOnlyComposable get() = LocalEasyMoMoPalette.current.terminalSurfaceVariant

val TerminalBorder: Color
    @Composable @ReadOnlyComposable get() = LocalEasyMoMoPalette.current.terminalBorder

val TextPrimary: Color
    @Composable @ReadOnlyComposable get() = LocalEasyMoMoPalette.current.textPrimary

val TextSecondary: Color
    @Composable @ReadOnlyComposable get() = LocalEasyMoMoPalette.current.textSecondary

val TextMuted: Color
    @Composable @ReadOnlyComposable get() = LocalEasyMoMoPalette.current.textMuted

// Semantic Status
val StatusSuccess: Color
    @Composable @ReadOnlyComposable get() = LocalEasyMoMoPalette.current.statusSuccess

val StatusSuccessBg: Color
    @Composable @ReadOnlyComposable get() = LocalEasyMoMoPalette.current.statusSuccessBg

val StatusWarning: Color
    @Composable @ReadOnlyComposable get() = LocalEasyMoMoPalette.current.statusWarning

val StatusWarningBg: Color
    @Composable @ReadOnlyComposable get() = LocalEasyMoMoPalette.current.statusWarningBg

val StatusError: Color
    @Composable @ReadOnlyComposable get() = LocalEasyMoMoPalette.current.statusError

val StatusErrorBg: Color
    @Composable @ReadOnlyComposable get() = LocalEasyMoMoPalette.current.statusErrorBg

val StatusInfo: Color
    @Composable @ReadOnlyComposable get() = LocalEasyMoMoPalette.current.statusInfo

val StatusInfoBg: Color
    @Composable @ReadOnlyComposable get() = LocalEasyMoMoPalette.current.statusInfoBg
