package com.milesxue.pixeldone.ui.theme

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import com.milesxue.pixeldone.domain.todo.AppLanguage

internal enum class PixelTextScript {
    SOURCE,
    CJK,
    ARABIC,
    SYSTEM,
}

internal enum class PixelTextRole {
    SANS,
    SERIF,
}

internal data class PixelScriptRun(
    val start: Int,
    val endExclusive: Int,
    val script: PixelTextScript,
)

private data class CodePointToken(
    val start: Int,
    val endExclusive: Int,
    val script: PixelTextScript?,
)

internal fun splitScriptRuns(text: String): List<PixelScriptRun> {
    if (text.isEmpty()) return emptyList()
    val tokens = buildList {
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            val end = index + Character.charCount(codePoint)
            add(CodePointToken(index, end, codePoint.pixelTextScript()))
            index = end
        }
    }
    val resolved = tokens.mapIndexed { index, token ->
        token.script ?: nearestWrittenScript(tokens, index) ?: PixelTextScript.SOURCE
    }
    return buildList {
        var runStart = tokens.first().start
        var runScript = resolved.first()
        for (index in 1 until tokens.size) {
            if (resolved[index] != runScript) {
                add(PixelScriptRun(runStart, tokens[index].start, runScript))
                runStart = tokens[index].start
                runScript = resolved[index]
            }
        }
        add(PixelScriptRun(runStart, tokens.last().endExclusive, runScript))
    }
}

internal fun scriptAwareText(
    text: String,
    role: PixelTextRole,
): AnnotatedString = buildAnnotatedString {
    append(text)
    splitScriptRuns(text).forEach { run ->
        run.script.fontFamily(role)?.let { family ->
            addStyle(SpanStyle(fontFamily = family), run.start, run.endExclusive)
        }
    }
}

internal fun scriptAwareValueText(
    prefix: String,
    value: String,
    role: PixelTextRole = PixelTextRole.SANS,
): AnnotatedString = buildAnnotatedString {
    append(prefix)
    append(scriptAwareText(value, role))
}

internal fun AppLanguage.nativeLabelFontFamily(): FontFamily? = when (this) {
    AppLanguage.SIMPLIFIED_CHINESE -> PixelNotoSansSc
    AppLanguage.ARABIC -> PixelNotoSansArabic
    AppLanguage.ENGLISH,
    AppLanguage.FRENCH,
    AppLanguage.RUSSIAN,
    AppLanguage.SPANISH,
    -> PixelSourceSans
    AppLanguage.SYSTEM -> null
}

private fun nearestWrittenScript(tokens: List<CodePointToken>, index: Int): PixelTextScript? {
    for (previous in index - 1 downTo 0) {
        tokens[previous].script?.takeUnless { it == PixelTextScript.SYSTEM }?.let { return it }
    }
    for (next in index + 1 until tokens.size) {
        tokens[next].script?.takeUnless { it == PixelTextScript.SYSTEM }?.let { return it }
    }
    return null
}

private fun Int.pixelTextScript(): PixelTextScript? {
    if (isEmojiCodePoint()) return PixelTextScript.SYSTEM
    return when (Character.UnicodeScript.of(this)) {
        Character.UnicodeScript.LATIN,
        Character.UnicodeScript.CYRILLIC,
        Character.UnicodeScript.GREEK,
        -> PixelTextScript.SOURCE
        Character.UnicodeScript.HAN,
        Character.UnicodeScript.HIRAGANA,
        Character.UnicodeScript.KATAKANA,
        Character.UnicodeScript.HANGUL,
        -> PixelTextScript.CJK
        Character.UnicodeScript.ARABIC -> PixelTextScript.ARABIC
        else -> null
    }
}

private fun Int.isEmojiCodePoint(): Boolean =
    this in 0x1F000..0x1FAFF ||
        this in 0x2600..0x27BF ||
        this in 0x1F3FB..0x1F3FF ||
        this == 0x200D ||
        this == 0xFE0F

private fun PixelTextScript.fontFamily(role: PixelTextRole): FontFamily? = when (this) {
    PixelTextScript.SOURCE -> if (role == PixelTextRole.SERIF) PixelSourceSerif else PixelSourceSans
    PixelTextScript.CJK -> if (role == PixelTextRole.SERIF) PixelNotoSerifSc else PixelNotoSansSc
    PixelTextScript.ARABIC -> if (role == PixelTextRole.SERIF) PixelNotoNaskhArabic else PixelNotoSansArabic
    PixelTextScript.SYSTEM -> null
}
