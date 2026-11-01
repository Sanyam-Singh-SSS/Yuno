package com.decio.skipii

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/**
 * Heavy rounded face for titles (header, section titles, video titles). Pixel ships a
 * rounded sans; other devices fall back to the regular sans at the same weight.
 */
val RoundedHeavy = FontFamily(
    Font(DeviceFontFamilyName("sans-serif-rounded"), weight = FontWeight.ExtraBold),
    Font(DeviceFontFamilyName("sans-serif-rounded"), weight = FontWeight.Bold),
    Font(DeviceFontFamilyName("sans-serif"), weight = FontWeight.ExtraBold),
    Font(DeviceFontFamilyName("sans-serif"), weight = FontWeight.Bold),
)

/**
 * SF Pro Display, the face BitChord ships as its default, with BitChord's own
 * weight mapping (Theme.kt there): the regular file carries W400 and W500, the
 * medium cut W600, semibold W700 and bold W800. The five OTFs are the exact
 * files BitChord bundles, so type lines up cut for cut.
 */
val SfProDisplay = FontFamily(
    Font(R.font.sf_pro_display_regular, FontWeight.W400),
    Font(R.font.sf_pro_display_regular, FontWeight.W500),
    Font(R.font.sf_pro_display_medium, FontWeight.W600),
    Font(R.font.sf_pro_display_semibold, FontWeight.W700),
    Font(R.font.sf_pro_display_bold, FontWeight.W800),
)

/** Applies [family] to every style in the scale, as BitChord's `withFamily` does. */
private fun Typography.withFamily(family: FontFamily) = Typography(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
    titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
)

/** The default type scale, drawn in SF Pro Display rather than the system face. */
private val AppTypography = Typography().withFamily(SfProDisplay)

/**
 * BitChord's fixed schemes (its Theme.kt): white on black, black on white.
 * No wallpaper colors and no named palettes. The container steps are the same
 * grays, so cards, sheets and the nav stay apart without taking on a tint.
 */
private val DarkColors = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF1C1C1E),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF8E8E93),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF2C2C2E),
    onSecondaryContainer = Color.White,
    tertiary = Color.White,
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF2C2C2E),
    onTertiaryContainer = Color.White,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF0D0D0F),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1C1C1E),
    onSurfaceVariant = Color(0xFF8E8E93),
    outline = Color(0xFF2C2C2E),
    outlineVariant = Color(0xFF3A3A3C),
    inverseSurface = Color.White,
    inverseOnSurface = Color.Black,
    inversePrimary = Color.Black,
    surfaceDim = Color.Black,
    surfaceBright = Color(0xFF3A3A3C),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0D0D0F),
    surfaceContainer = Color(0xFF1C1C1E),
    surfaceContainerHigh = Color(0xFF2C2C2E),
    surfaceContainerHighest = Color(0xFF3A3A3C),
)

private val LightColors = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF2F2F7),
    onPrimaryContainer = Color.Black,
    secondary = Color(0xFF6E6E73),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5E5EA),
    onSecondaryContainer = Color.Black,
    tertiary = Color.Black,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE5E5EA),
    onTertiaryContainer = Color.Black,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color(0xFFF7F7F9),
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFF2F2F7),
    onSurfaceVariant = Color(0xFF6E6E73),
    outline = Color(0xFFE5E5EA),
    outlineVariant = Color(0xFFD1D1D6),
    inverseSurface = Color.Black,
    inverseOnSurface = Color.White,
    inversePrimary = Color.White,
    surfaceDim = Color(0xFFD1D1D6),
    surfaceBright = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F7F9),
    surfaceContainer = Color(0xFFF2F2F7),
    surfaceContainerHigh = Color(0xFFE5E5EA),
    surfaceContainerHighest = Color(0xFFD1D1D6),
)

/** System, Light or Dark, persisted. BitChord's ThemeMode; an older "amoled" choice reads as Dark. */
object ThemeSettings {
    private var prefs: SharedPreferences? = null

    var mode by mutableStateOf("dark")
        private set

    fun init(context: Context) {
        val p = context.getSharedPreferences("appearance", Context.MODE_PRIVATE).also { prefs = it }
        mode = when (val stored = p.getString("mode", "dark")) {
            "light", "system", "dark" -> stored
            else -> "dark"
        }
    }

    fun chooseMode(value: String) {
        mode = value
        prefs?.edit()?.putString("mode", value)?.apply()
    }
}

@Composable
fun isDarkTheme(): Boolean = when (ThemeSettings.mode) {
    "light" -> false
    "dark" -> true
    else -> isSystemInDarkTheme()
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkipiiTheme(content: @Composable () -> Unit) {
    val dark = isDarkTheme()
    val scheme = if (dark) DarkColors else LightColors
    SystemBars(dark, scheme.surface)
    MaterialTheme(colorScheme = scheme, shapes = ExpressiveShapes, typography = AppTypography) {
        CompositionLocalProvider(
            // No tap ripples anywhere: press feedback is the scale-down from Motion.kt.
            LocalRippleConfiguration provides null,
            // Text and icons without an explicit color follow the theme. Without this they
            // default to black, which vanished on dark surfaces (the dark-mode bug).
            LocalContentColor provides scheme.onSurface,
            // Text in its own direction, laid out as in English (see AndroidManifest's supportsRtl).
            LocalTextStyle provides LocalTextStyle.current.merge(OwnDirectionText),
            content = content,
        )
    }
}

/**
 * The layout is the English one on every phone (supportsRtl="false", Derek, 2026-10-30: nothing
 * moves on an Arabic phone), but each text runs in its own direction (Unicode's first strong
 * letter): an Arabic title right to left, so its punctuation lands where it belongs, still
 * left-aligned like the English. Compose's default gave every text the layout's direction, so an
 * Arabic title's full stop showed at its start. Every Text here reads [LocalTextStyle] (none passes
 * a typography style); text fields set the same two fields on their own [TextStyle].
 */
internal val OwnDirectionText = TextStyle(textDirection = TextDirection.Content, textAlign = TextAlign.Left)

/**
 * Whether the phone's language is written right to left (Arabic, Hebrew, Persian, Urdu…). The app's
 * layout ignores it (supportsRtl="false"), so this is read from the locale, not the layout direction.
 */
@Composable
fun rememberRtlLanguage(): Boolean {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) { android.text.TextUtils.getLayoutDirectionFromLocale(locale) == android.view.View.LAYOUT_DIRECTION_RTL }
}

/** True while the open player paints the status bar black over its video: icons go white. */
object StatusBarOverVideo {
    var on by mutableStateOf(false)
    /** The Shorts tab: video under both system bars, so both bars' icons go white. */
    var shorts by mutableStateOf(false)
}

/**
 * Status/navigation bar icon colors and the window background follow the app's mode,
 * not the phone's: with Light forced on a dark-mode phone the icons would otherwise be
 * white on white (from Ledgii's SystemBars).
 */
@Composable
private fun SystemBars(dark: Boolean, windowBg: Color) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val overVideo = StatusBarOverVideo.on || StatusBarOverVideo.shorts
    val navOverVideo = StatusBarOverVideo.shorts
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !dark && !overVideo
        controller.isAppearanceLightNavigationBars = !dark && !navOverVideo
    }
    DisposableEffect(windowBg) {
        (view.context as? Activity)?.window?.setBackgroundDrawable(ColorDrawable(windowBg.toArgb()))
        onDispose { }
    }
}
