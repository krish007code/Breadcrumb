package dev.breadcrumb.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

internal const val PaperArgb = 0xFFF6F3EC
internal const val InkArgb = 0xFF1C1B19
internal const val MutedArgb = 0xFF6B665C
internal const val SandArgb = 0xFFECE7DC
internal const val BorderArgb = 0xFFE2DCCF
internal const val AmberArgb = 0xFFFFD27A
internal const val OrchidArgb = 0xFFBE54E3
internal const val SkyArgb = 0xFF3FA2F2
internal const val MeadowArgb = 0xFF4CC46A
internal const val TangerineArgb = 0xFFFF9F2E
internal const val FlameArgb = 0xFFF25A2B

val Paper = Color(PaperArgb)
val Ink = Color(InkArgb)
val Muted = Color(MutedArgb)
val Sand = Color(SandArgb)
val Border = Color(BorderArgb)
val Amber = Color(AmberArgb)

@Composable
fun BreadcrumbTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme =
            lightColorScheme(
                primary = Ink,
                onPrimary = Color.White,
                background = Paper,
                onBackground = Ink,
                surface = Color.White,
                onSurface = Ink,
                surfaceVariant = Sand,
                onSurfaceVariant = Muted,
                outline = Border,
                secondaryContainer = Amber,
                onSecondaryContainer = Ink,
            ),
        typography =
            Typography(
                headlineLarge =
                    TextStyle(fontFamily = FontFamily.Serif, fontSize = 36.sp, lineHeight = 42.sp),
                headlineMedium =
                    TextStyle(fontFamily = FontFamily.Serif, fontSize = 28.sp, lineHeight = 34.sp),
                titleLarge =
                    TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
                titleMedium =
                    TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
                bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
                bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
                labelLarge =
                    TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
            ),
        content = content,
    )
}

internal val Tangerine = Color(TangerineArgb)
internal val Flame = Color(FlameArgb)
