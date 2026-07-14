package com.milesxue.pixeldone.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.milesxue.pixeldone.R

private val SourceSans = FontFamily(Font(R.font.source_sans_3_variable))
private val SourceSerif = FontFamily(Font(R.font.source_serif_4_variable))
private val NotoSansSc = FontFamily(Font(R.font.noto_sans_sc_variable))
private val NotoSerifSc = FontFamily(Font(R.font.noto_serif_sc_variable))
private val NotoSansArabic = FontFamily(Font(R.font.noto_sans_arabic_variable))
private val NotoNaskhArabic = FontFamily(Font(R.font.noto_naskh_arabic_variable))

private data class PixelFontFamilies(
    val sans: FontFamily,
    val serif: FontFamily,
)

private fun pixelFontFamilies(languageTag: String): PixelFontFamilies = when {
    languageTag.startsWith("zh", ignoreCase = true) -> PixelFontFamilies(NotoSansSc, NotoSerifSc)
    languageTag.startsWith("ar", ignoreCase = true) -> PixelFontFamilies(NotoSansArabic, NotoNaskhArabic)
    else -> PixelFontFamilies(SourceSans, SourceSerif)
}

fun pixelDoneTypography(languageTag: String): Typography {
    val families = pixelFontFamilies(languageTag)
    val sansBase = TextStyle(
        fontFamily = families.sans,
        fontWeight = FontWeight.Normal,
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
            fontSize = 11.sp,
            lineHeight = 14.sp,
        ),
    )
}
