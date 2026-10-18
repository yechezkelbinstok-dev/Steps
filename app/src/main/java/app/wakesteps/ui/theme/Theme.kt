package app.wakesteps.ui.theme

import android.content.Context
import androidx.annotation.ColorRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.wakesteps.R
import app.wakesteps.data.WidgetStyle

/**
 * The app wears the widget's look: always dark, built from the same wallpaper tones (Android 12+;
 * teal before that), the ring drawn in the widget style the user picked and gold once the goal
 * is met.
 */
@Immutable
class AppColors(
    val style: WidgetStyle,
    /** Page background: near black with a hint of the wallpaper colour. */
    val background: Color,
    /** The widget's card colour, behind the hero ring as a soft glow. */
    val glow: Color,
    val card: Color,
    val cardHigh: Color,
    /** Hairline edge on cards. */
    val outline: Color,
    val text: Color,
    val muted: Color,
    val faint: Color,
    val track: Color,
    /** The ring, footprints, icons and chart bars until the goal is met. */
    val accent: Color,
    /** Where the vivid ring's blend ends up; the same as [accent] in the other styles. */
    val accentEnd: Color,
    val calories: Color,
    val distance: Color,
    /** Footprints and glow once the goal is met. */
    val goal: Color,
    /** The gold ring's blend, from 12 o'clock to the far side. */
    val goalStart: Color,
    val goalEnd: Color,
    /** Where a lap past the goal deepens to at its tip. */
    val lapEnd: Color,
    val error: Color,
) {
    companion object {
        fun of(context: Context, style: WidgetStyle): AppColors {
            fun c(@ColorRes id: Int) = Color(context.getColor(id))
            val tint = c(R.color.app_tint)
            val card = c(R.color.widget_bg)
            val background = lerp(Color.Black, tint, 0.38f)
            val vivid = style == WidgetStyle.VIVID
            val accent = c(if (vivid) R.color.widget_aqua else R.color.widget_accent)
            return AppColors(
                style = style,
                background = background,
                glow = card,
                card = lerp(background, card, 0.42f),
                cardHigh = lerp(background, card, 0.7f),
                outline = Color.White.copy(alpha = 0.06f),
                text = c(R.color.widget_text),
                muted = c(R.color.widget_muted),
                faint = c(R.color.widget_muted).copy(alpha = 0.55f),
                track = c(R.color.widget_track),
                accent = accent,
                accentEnd = if (vivid) c(R.color.widget_ring_end) else accent,
                calories = if (vivid) c(R.color.widget_calories) else accent,
                distance = if (vivid) c(R.color.widget_distance) else accent,
                goal = c(R.color.widget_gold),
                goalStart = c(R.color.widget_gold_start),
                goalEnd = c(R.color.widget_gold_end),
                lapEnd = c(R.color.widget_lap_end),
                error = c(R.color.widget_error),
            )
        }
    }
}

val LocalAppColors = staticCompositionLocalOf<AppColors> { error("No AppColors") }

/** Shorthand: `Palette.accent` inside composables. */
val Palette: AppColors
    @Composable get() = LocalAppColors.current

/** Google Sans Flex, bundled so every phone gets the same type as a Pixel. */
private val Sans = FontFamily(
    Font(R.font.gsans_regular, FontWeight.Normal),
    Font(R.font.gsans_medium, FontWeight.Medium),
    Font(R.font.gsans_semibold, FontWeight.SemiBold),
)

/** Cut for large sizes: tighter and finer, for the big numbers. */
val Display = FontFamily(Font(R.font.gsans_display, FontWeight.Normal))

private fun typography(): Typography {
    val base = Typography()
    fun TextStyle.sans(weight: FontWeight = fontWeight ?: FontWeight.Normal) =
        copy(fontFamily = Sans, fontWeight = weight)
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = Display, letterSpacing = (-0.02).em),
        displayMedium = base.displayMedium.copy(fontFamily = Display, letterSpacing = (-0.02).em),
        displaySmall = base.displaySmall.copy(fontFamily = Display, letterSpacing = (-0.01).em),
        headlineLarge = base.headlineLarge.sans(),
        headlineMedium = base.headlineMedium.sans(),
        headlineSmall = base.headlineSmall.sans(),
        titleLarge = base.titleLarge.sans(FontWeight.Medium),
        titleMedium = base.titleMedium.sans(FontWeight.Medium).copy(fontSize = 17.sp),
        titleSmall = base.titleSmall.sans(FontWeight.Medium),
        bodyLarge = base.bodyLarge.sans().copy(letterSpacing = 0.sp),
        bodyMedium = base.bodyMedium.sans().copy(letterSpacing = 0.sp),
        bodySmall = base.bodySmall.sans().copy(letterSpacing = 0.sp),
        labelLarge = base.labelLarge.sans(FontWeight.Medium),
        labelMedium = base.labelMedium.sans(FontWeight.Medium),
        labelSmall = base.labelSmall.sans(FontWeight.Medium),
    )
}

@Composable
fun WakeStepsTheme(style: WidgetStyle = WidgetStyle.SOFT, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = remember(style) { AppColors.of(context, style) }
    val scheme = darkColorScheme(
        primary = colors.accent,
        onPrimary = colors.background,
        primaryContainer = colors.cardHigh,
        onPrimaryContainer = colors.text,
        secondary = colors.muted,
        secondaryContainer = colors.cardHigh,
        onSecondaryContainer = colors.text,
        tertiary = colors.goal,
        background = colors.background,
        onBackground = colors.text,
        surface = colors.background,
        onSurface = colors.text,
        surfaceVariant = colors.card,
        onSurfaceVariant = colors.muted,
        surfaceContainerLowest = colors.background,
        surfaceContainerLow = colors.card,
        surfaceContainer = colors.card,
        surfaceContainerHigh = colors.cardHigh,
        surfaceContainerHighest = colors.cardHigh,
        outline = colors.faint,
        outlineVariant = colors.outline,
        error = colors.error,
    )
    CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = remember { typography() }, content = content)
    }
}
