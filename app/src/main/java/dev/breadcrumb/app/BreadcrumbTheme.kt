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

val Paper = Color(0xFFF6F3EC)
val Ink = Color(0xFF1C1B19)
val Muted = Color(0xFF6B665C)
val Sand = Color(0xFFECE7DC)
val Border = Color(0xFFE2DCCF)
val Amber = Color(0xFFFFD27A)

@Composable
fun BreadcrumbTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Ink, onPrimary = Color.White,
            background = Paper, onBackground = Ink,
            surface = Color.White, onSurface = Ink,
            surfaceVariant = Sand, onSurfaceVariant = Muted,
            outline = Border, secondaryContainer = Amber, onSecondaryContainer = Ink,
        ),
        typography = Typography(
            headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 36.sp, lineHeight = 42.sp),
            headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontSize = 28.sp, lineHeight = 34.sp),
            titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
            titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
            bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
            bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
            labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
        ),
        content = content,
    )
}
