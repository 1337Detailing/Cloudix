package com.cloudix.app

import android.app.Activity
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.view.WindowInsetsControllerCompat

val LocalCloudixScrollbar = compositionLocalOf { Color(0xFF737086) }

private val almostWhite = Color(0xFFFDFCFF)
private val night = Color(0xFF11121A)
private fun onTopOf(color: Color): Color =
    if (color.luminance() > 0.18f) Color(0xFF101015) else Color.White

fun hexColor(hex: String): Color =
    Color(android.graphics.Color.parseColor(CloudixHex.normalize(hex)
        ?: throw IllegalArgumentException("Couleur invalide")))

/**
 * The palette is computed from the observable AppearanceSnapshot.
 * Dynamic Material You is still the source when enabled, but surface styles and
 * custom roles apply on top of it (including on Android 12/OriginOS).
 */
fun cloudixScheme(base: ColorScheme, appearance: AppearanceSnapshot, dark: Boolean): ColorScheme {
    val seed = hexColor(CloudixAppearanceCatalog.presets[
        appearance.accent.coerceIn(0, CloudixAppearanceCatalog.presets.lastIndex)
    ].hex)
    val primary = if (appearance.dynamic) base.primary else
        if (dark) lerp(seed, Color.White, .30f) else seed
    val lightSurface = Color(0xFFFBFAFE)
    val style = appearance.surfaceStyle
    val backdrop: Color
    val surface: Color
    val low: Color
    val container: Color
    val high: Color
    val highest: Color
    if (dark) {
        when (style) {
            "strong" -> {
                backdrop = lerp(Color(0xFF0B0C10), primary, .095f)
                surface = lerp(Color(0xFF14151A), primary, .16f)
                low = lerp(Color(0xFF181920), primary, .17f)
                container = lerp(Color(0xFF202128), primary, .21f)
                high = lerp(Color(0xFF292A31), primary, .22f)
                highest = lerp(Color(0xFF34353D), primary, .25f)
            }
            "amoled" -> {
                backdrop = Color.Black
                surface = Color.Black
                low = Color(0xFF050505)
                container = lerp(Color(0xFF0D0D0D), primary, .085f)
                high = lerp(Color(0xFF141414), primary, .09f)
                highest = lerp(Color(0xFF1A1A1A), primary, .105f)
            }
            "amoled_plain" -> {
                backdrop = Color.Black
                surface = Color.Black
                low = Color.Black
                container = Color(0xFF0A0A0A)
                high = Color(0xFF121212)
                highest = Color(0xFF1B1B1B)
            }
            else -> {
                backdrop = lerp(Color(0xFF0B0C12), primary, .018f)
                surface = lerp(Color(0xFF12131B), primary, .038f)
                low = lerp(Color(0xFF191A22), primary, .050f)
                container = lerp(Color(0xFF202129), primary, .065f)
                high = lerp(Color(0xFF282931), primary, .075f)
                highest = lerp(Color(0xFF30313B), primary, .085f)
            }
        }
    } else {
        when (style) {
            "strong" -> {
                backdrop = lerp(lightSurface, primary, .075f)
                surface = lerp(almostWhite, primary, .10f)
                low = lerp(Color(0xFFF7F5F9), primary, .10f)
                container = lerp(Color(0xFFF0EDF5), primary, .14f)
                high = lerp(Color(0xFFEAE7EF), primary, .17f)
                highest = lerp(Color(0xFFE6E2EB), primary, .19f)
            }
            else -> {
                backdrop = lerp(lightSurface, primary, .018f)
                surface = almostWhite
                low = lerp(Color(0xFFF8F7FA), primary, .027f)
                container = lerp(Color(0xFFF0EEF5), primary, .045f)
                high = lerp(Color(0xFFEBE9F1), primary, .065f)
                highest = lerp(Color(0xFFE3E0EB), primary, .075f)
            }
        }
    }
    val foreground = if (dark) Color(0xFFF4F1F7) else Color(0xFF202028)
    val subdued = if (dark) Color(0xFFC8C5D0) else Color(0xFF5F5A66)
    var scheme = base.copy(
        primary = primary,
        onPrimary = onTopOf(primary),
        primaryContainer = when {
            dark && style == "amoled_plain" -> Color(0xFF141414)
            dark -> lerp(primary, backdrop, .72f)
            else -> lerp(primary, Color.White, .83f)
        },
        onPrimaryContainer = if (dark) Color(0xFFF5EFFF) else lerp(primary, Color.Black, .56f),
        secondaryContainer = if (dark && style == "amoled_plain") Color(0xFF101010)
            else base.secondaryContainer,
        onSecondaryContainer = if (dark && style == "amoled_plain") Color(0xFFF5F1F7)
            else base.onSecondaryContainer,
        background = backdrop,
        onBackground = foreground,
        surface = surface,
        onSurface = foreground,
        onSurfaceVariant = subdued,
        surfaceContainerLowest = backdrop,
        surfaceContainerLow = low,
        surfaceContainer = container,
        surfaceContainerHigh = high,
        surfaceContainerHighest = highest,
        surfaceVariant = if (dark) highest else container,
        outline = if (dark) Color(0xFF888390) else Color(0xFF817A88)
    )

    fun custom(role: CloudixColorRole): Color? =
        appearance.overrides[role.key]?.let { runCatching { hexColor(it) }.getOrNull() }

    custom(CloudixColorRole.ACCENT)?.let { accent ->
        val tint = if (dark) lerp(accent, Color.White, .2f) else accent
        val select = if (dark) lerp(tint, scheme.background, .70f) else
            lerp(tint, Color.White, .82f)
        scheme = scheme.copy(primary = tint, onPrimary = onTopOf(tint),
            primaryContainer = select, onPrimaryContainer = onTopOf(select),
            secondary = tint, tertiary = tint)
    }
    custom(CloudixColorRole.BACKGROUND)?.let {
        scheme = scheme.copy(background = it, surfaceContainerLowest = it)
    }
    custom(CloudixColorRole.SURFACE)?.let {
        scheme = scheme.copy(surface = it, surfaceContainerLow = it,
            surfaceContainer = it, surfaceContainerHigh = it,
            surfaceContainerHighest = it, surfaceVariant = it)
    }
    custom(CloudixColorRole.TEXT)?.let {
        scheme = scheme.copy(onBackground = it, onSurface = it,
            onSurfaceVariant = lerp(it, scheme.surface, .26f))
    }
    custom(CloudixColorRole.SELECTION)?.let {
        scheme = scheme.copy(primaryContainer = it, onPrimaryContainer = onTopOf(it),
            secondaryContainer = it, onSecondaryContainer = onTopOf(it))
    }
    custom(CloudixColorRole.OUTLINE)?.let { scheme = scheme.copy(outline = it) }
    return scheme
}

@Composable
fun CloudixTheme(settings: CloudixSettings, content: @Composable () -> Unit) {
    // Reading the Compose state ensures *all* surfaces recompute on any settings change.
    val appearance = settings.appearance
    val context = LocalContext.current
    val view = LocalView.current
    val dark = when (appearance.theme) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val systemBase = if (appearance.dynamic && Build.VERSION.SDK_INT >= 31) {
        runCatching {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }.getOrElse {
            if (dark) darkColorScheme() else lightColorScheme()
        }
    } else if (dark) darkColorScheme() else lightColorScheme()
    val target = cloudixScheme(systemBase, appearance, dark)
    @Composable fun animated(color: Color, name: String): Color =
        if (appearance.animations) animateColorAsState(color, tween(380), label = name).value
        else color
    val scheme = target.copy(
        primary = animated(target.primary, "primary"),
        onPrimary = animated(target.onPrimary, "onPrimary"),
        primaryContainer = animated(target.primaryContainer, "primaryContainer"),
        onPrimaryContainer = animated(target.onPrimaryContainer, "onPrimaryContainer"),
        secondary = animated(target.secondary, "secondary"),
        background = animated(target.background, "background"),
        onBackground = animated(target.onBackground, "onBackground"),
        surface = animated(target.surface, "surface"),
        onSurface = animated(target.onSurface, "onSurface"),
        onSurfaceVariant = animated(target.onSurfaceVariant, "onSurfaceVariant"),
        surfaceContainer = animated(target.surfaceContainer, "surfaceContainer"),
        surfaceContainerHigh = animated(target.surfaceContainerHigh, "surfaceContainerHigh"),
        surfaceContainerLow = animated(target.surfaceContainerLow, "surfaceContainerLow"),
        surfaceContainerHighest = animated(target.surfaceContainerHighest, "surfaceContainerHighest"),
        outline = animated(target.outline, "outline")
    )
    val thumb = appearance.overrides[CloudixColorRole.SCROLLBAR.key]
        ?.let { runCatching { hexColor(it) }.getOrNull() }
        ?: scheme.primary

    SideEffect {
        val activity = context as? Activity
        if (activity != null) {
            val controller = WindowInsetsControllerCompat(activity.window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
    CompositionLocalProvider(LocalCloudixScrollbar provides thumb) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography(
                headlineMedium = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold),
                titleLarge = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            ),
            shapes = Shapes(
                extraSmall = RoundedCornerShape(9.dp),
                small = RoundedCornerShape(14.dp),
                medium = RoundedCornerShape(20.dp),
                large = RoundedCornerShape(28.dp),
                extraLarge = RoundedCornerShape(34.dp)
            ),
            content = content
        )
    }
}
