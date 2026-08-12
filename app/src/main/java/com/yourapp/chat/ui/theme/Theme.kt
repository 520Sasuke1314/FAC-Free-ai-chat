package com.yourapp.chat.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import com.yourapp.chat.ChatApplication

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

/** 全局圆角：整体更圆润（气泡、输入框、对话框、按钮统一生效） */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

/**
 * 浅色 / 深色之间的整体颜色过渡（设置里切「深色模式」时，所有颜色由浅到深 / 由深到浅平滑滑动）。
 * 每个配色槽位独立跑 animateColorAsState，Spring 慢刚度让过渡更像"滑动"而非急闪。
 */
@Composable
private fun animatedColorScheme(darkTheme: Boolean): ColorScheme {
    @Composable
    fun c(light: androidx.compose.ui.graphics.Color, dark: androidx.compose.ui.graphics.Color, label: String): androidx.compose.ui.graphics.Color {
        val animated by animateColorAsState(
            targetValue = if (darkTheme) dark else light,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = label
        )
        return animated
    }
    return ColorScheme(
        primary = c(LightColorScheme.primary, DarkColorScheme.primary, "primary"),
        onPrimary = c(LightColorScheme.onPrimary, DarkColorScheme.onPrimary, "onPrimary"),
        primaryContainer = c(LightColorScheme.primaryContainer, DarkColorScheme.primaryContainer, "primaryContainer"),
        onPrimaryContainer = c(LightColorScheme.onPrimaryContainer, DarkColorScheme.onPrimaryContainer, "onPrimaryContainer"),
        inversePrimary = c(LightColorScheme.inversePrimary, DarkColorScheme.inversePrimary, "inversePrimary"),
        secondary = c(LightColorScheme.secondary, DarkColorScheme.secondary, "secondary"),
        onSecondary = c(LightColorScheme.onSecondary, DarkColorScheme.onSecondary, "onSecondary"),
        secondaryContainer = c(LightColorScheme.secondaryContainer, DarkColorScheme.secondaryContainer, "secondaryContainer"),
        onSecondaryContainer = c(LightColorScheme.onSecondaryContainer, DarkColorScheme.onSecondaryContainer, "onSecondaryContainer"),
        tertiary = c(LightColorScheme.tertiary, DarkColorScheme.tertiary, "tertiary"),
        onTertiary = c(LightColorScheme.onTertiary, DarkColorScheme.onTertiary, "onTertiary"),
        tertiaryContainer = c(LightColorScheme.tertiaryContainer, DarkColorScheme.tertiaryContainer, "tertiaryContainer"),
        onTertiaryContainer = c(LightColorScheme.onTertiaryContainer, DarkColorScheme.onTertiaryContainer, "onTertiaryContainer"),
        background = c(LightColorScheme.background, DarkColorScheme.background, "background"),
        onBackground = c(LightColorScheme.onBackground, DarkColorScheme.onBackground, "onBackground"),
        surface = c(LightColorScheme.surface, DarkColorScheme.surface, "surface"),
        onSurface = c(LightColorScheme.onSurface, DarkColorScheme.onSurface, "onSurface"),
        surfaceVariant = c(LightColorScheme.surfaceVariant, DarkColorScheme.surfaceVariant, "surfaceVariant"),
        onSurfaceVariant = c(LightColorScheme.onSurfaceVariant, DarkColorScheme.onSurfaceVariant, "onSurfaceVariant"),
        surfaceTint = c(LightColorScheme.surfaceTint, DarkColorScheme.surfaceTint, "surfaceTint"),
        inverseSurface = c(LightColorScheme.inverseSurface, DarkColorScheme.inverseSurface, "inverseSurface"),
        inverseOnSurface = c(LightColorScheme.inverseOnSurface, DarkColorScheme.inverseOnSurface, "inverseOnSurface"),
        error = c(LightColorScheme.error, DarkColorScheme.error, "error"),
        onError = c(LightColorScheme.onError, DarkColorScheme.onError, "onError"),
        errorContainer = c(LightColorScheme.errorContainer, DarkColorScheme.errorContainer, "errorContainer"),
        onErrorContainer = c(LightColorScheme.onErrorContainer, DarkColorScheme.onErrorContainer, "onErrorContainer"),
        outline = c(LightColorScheme.outline, DarkColorScheme.outline, "outline"),
        outlineVariant = c(LightColorScheme.outlineVariant, DarkColorScheme.outlineVariant, "outlineVariant"),
        scrim = c(LightColorScheme.scrim, DarkColorScheme.scrim, "scrim"),
        surfaceBright = c(LightColorScheme.surfaceBright, DarkColorScheme.surfaceBright, "surfaceBright"),
        surfaceDim = c(LightColorScheme.surfaceDim, DarkColorScheme.surfaceDim, "surfaceDim"),
        surfaceContainer = c(LightColorScheme.surfaceContainer, DarkColorScheme.surfaceContainer, "surfaceContainer"),
        surfaceContainerHigh = c(LightColorScheme.surfaceContainerHigh, DarkColorScheme.surfaceContainerHigh, "surfaceContainerHigh"),
        surfaceContainerHighest = c(LightColorScheme.surfaceContainerHighest, DarkColorScheme.surfaceContainerHighest, "surfaceContainerHighest"),
        surfaceContainerLow = c(LightColorScheme.surfaceContainerLow, DarkColorScheme.surfaceContainerLow, "surfaceContainerLow"),
        surfaceContainerLowest = c(LightColorScheme.surfaceContainerLowest, DarkColorScheme.surfaceContainerLowest, "surfaceContainerLowest")
    )
}

@Composable
fun ChatAppTheme(content: @Composable () -> Unit) {
    // 设置页的「深色模式」独立开关：全局可读可写状态，切换时整包颜色平滑过渡
    val darkEnabled = ChatApplication.instance.darkModeEnabled.value
    val colorScheme = animatedColorScheme(darkEnabled)
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}