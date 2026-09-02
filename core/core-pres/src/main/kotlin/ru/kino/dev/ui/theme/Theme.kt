package ru.kino.dev.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = TealLight5FD1D8,
    onPrimary = TealOnContainer00363A,
    primaryContainer = TealContainerDark00696F,
    onPrimaryContainer = TealContainerLightB2EBF0,
    secondary = PurpleLightC7BFEA,
    onSecondary = PurpleOnSecondary2C2350,
    secondaryContainer = Purple4B3F72,
    onSecondaryContainer = PurpleOnContainerE9E4FF,
    tertiary = GoldFFC857,
    onTertiary = GoldOnContainer4A3600,
    tertiaryContainer = GoldContainerDark6B5000,
    onTertiaryContainer = GoldContainerLightFFE8B3,
    background = Indigo1F2041,
    onBackground = OnSurfaceDarkE3E1E9,
    surface = Indigo1F2041,
    onSurface = OnSurfaceDarkE3E1E9,
    surfaceVariant = SurfaceVariantDark32355A,
    onSurfaceVariant = OnSurfaceVariantDarkC3C6D6,
    outline = OutlineDark8B909C
)

private val LightColorScheme = lightColorScheme(
    primary = Teal119DA4,
    onPrimary = Color.White,
    primaryContainer = TealContainerLightB2EBF0,
    onPrimaryContainer = TealOnContainer00363A,
    secondary = DeepTeal19647E,
    onSecondary = Color.White,
    secondaryContainer = DeepTealContainerLightB8E3F0,
    onSecondaryContainer = DeepTealOnContainer00232D,
    tertiary = GoldFFC857,
    onTertiary = Indigo1F2041,
    tertiaryContainer = GoldContainerLightFFE8B3,
    onTertiaryContainer = GoldOnContainer4A3600,
    background = SurfaceLightFBFBFE,
    onBackground = OnSurfaceLight1A1C1E,
    surface = SurfaceLightFBFBFE,
    onSurface = OnSurfaceLight1A1C1E,
    surfaceVariant = SurfaceVariantLightDDE3E9,
    onSurfaceVariant = OnSurfaceVariantLight41474D,
    outline = OutlineLight71787E
)

@Composable
fun PassappTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Brand palette is fixed — Material You dynamic color is opted out by default
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}