package com.milesxue.pixeldone.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.milesxue.pixeldone.R

@OptIn(ExperimentalTextApi::class)
private fun variableFontFamily(resourceId: Int): FontFamily = FontFamily(
    Font(
        resId = resourceId,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        resId = resourceId,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
        resId = resourceId,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
    Font(
        resId = resourceId,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
)

internal val PixelSourceSans = variableFontFamily(R.font.source_sans_3_variable)
internal val PixelSourceSerif = variableFontFamily(R.font.source_serif_4_variable)
internal val PixelNotoSansSc = variableFontFamily(R.font.noto_sans_sc_variable)
internal val PixelNotoSerifSc = variableFontFamily(R.font.noto_serif_sc_variable)
internal val PixelNotoSansArabic = variableFontFamily(R.font.noto_sans_arabic_variable)
internal val PixelNotoNaskhArabic = variableFontFamily(R.font.noto_naskh_arabic_variable)

internal data class PixelFontFamilies(
    val sans: FontFamily,
    val serif: FontFamily,
)

internal fun pixelFontFamilies(languageTag: String): PixelFontFamilies = when {
    languageTag.startsWith("zh", ignoreCase = true) -> PixelFontFamilies(PixelNotoSansSc, PixelNotoSerifSc)
    languageTag.startsWith("ar", ignoreCase = true) -> PixelFontFamilies(PixelNotoSansArabic, PixelNotoNaskhArabic)
    else -> PixelFontFamilies(PixelSourceSans, PixelSourceSerif)
}

fun pixelDoneTypography(languageTag: String): Typography {
    val families = pixelFontFamilies(languageTag)
    val sansBase = TextStyle(
        fontFamily = families.sans,
        fontWeight = FontWeight.Normal,
        fontSynthesis = FontSynthesis.None,
        letterSpacing = 0.sp,
    )
    val serifBase = sansBase.copy(fontFamily = families.serif)

    return Typography(
        headlineSmall = serifBase.copy(
            fontSize = 24.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.Bold,
        ),
        titleLarge = serifBase.copy(
            fontSize = 22.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Bold,
        ),
        titleMedium = serifBase.copy(
            fontSize = 16.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        bodyLarge = sansBase.copy(
            fontSize = 16.sp,
            lineHeight = 24.sp,
        ),
        bodyMedium = sansBase.copy(
            fontSize = 14.sp,
            lineHeight = 20.sp,
        ),
        bodySmall = sansBase.copy(
            fontSize = 12.sp,
            lineHeight = 16.sp,
        ),
        labelLarge = sansBase.copy(
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        labelMedium = sansBase.copy(
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        labelSmall = sansBase.copy(
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
        ),
    )
}
