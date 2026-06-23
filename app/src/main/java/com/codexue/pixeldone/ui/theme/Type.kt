package com.codexue.pixeldone.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val PixelBaseText = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Normal,
    letterSpacing = 0.sp,
)

val Typography = Typography(
    titleLarge = PixelBaseText.copy(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
    ),
    titleMedium = PixelBaseText.copy(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    bodyLarge = PixelBaseText.copy(
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = PixelBaseText.copy(
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = PixelBaseText.copy(
        fontSize = 14.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    labelMedium = PixelBaseText.copy(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    labelSmall = PixelBaseText.copy(
        fontSize = 11.sp,
        lineHeight = 14.sp,
    ),
)
