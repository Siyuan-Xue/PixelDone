package com.milesxue.pixeldone

import com.milesxue.pixeldone.domain.todo.AppLanguage
import com.milesxue.pixeldone.ui.theme.PixelNotoSansArabic
import com.milesxue.pixeldone.ui.theme.PixelNotoSansSc
import com.milesxue.pixeldone.ui.theme.PixelScriptRun
import com.milesxue.pixeldone.ui.theme.PixelSourceSans
import com.milesxue.pixeldone.ui.theme.PixelTextScript
import com.milesxue.pixeldone.ui.theme.nativeLabelFontFamily
import com.milesxue.pixeldone.ui.theme.splitScriptRuns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ScriptAwareTextTest {
    @Test
    fun segmentsLatinCyrillicCjkArabicAndEmojiByUnicodeScript() {
        val text = "Task Привет 中文 العربية 🙂"

        assertEquals(
            listOf(
                PixelScriptRun(0, 12, PixelTextScript.SOURCE),
                PixelScriptRun(12, 15, PixelTextScript.CJK),
                PixelScriptRun(15, 23, PixelTextScript.ARABIC),
                PixelScriptRun(23, 25, PixelTextScript.SYSTEM),
            ),
            splitScriptRuns(text),
        )
    }

    @Test
    fun punctuationAndDigitsInheritTheAdjacentWrittenScript() {
        assertEquals(
            listOf(
                PixelScriptRun(0, 8, PixelTextScript.SOURCE),
                PixelScriptRun(8, 11, PixelTextScript.CJK),
            ),
            splitScriptRuns("Task 12：中文!"),
        )
        assertEquals(
            listOf(PixelScriptRun(0, 4, PixelTextScript.CJK)),
            splitScriptRuns("（中文）"),
        )
    }

    @Test
    fun emojiVariationAndJoinerStayOnTheSystemColorFont() {
        assertEquals(
            listOf(
                PixelScriptRun(0, 1, PixelTextScript.SOURCE),
                PixelScriptRun(1, 4, PixelTextScript.SYSTEM),
                PixelScriptRun(4, 5, PixelTextScript.SOURCE),
            ),
            splitScriptRuns("A❤️‍B"),
        )
    }

    @Test
    fun languageNamesAlwaysUseTheirOwnScriptFont() {
        assertSame(PixelNotoSansSc, AppLanguage.SIMPLIFIED_CHINESE.nativeLabelFontFamily())
        assertSame(PixelNotoSansArabic, AppLanguage.ARABIC.nativeLabelFontFamily())
        assertSame(PixelSourceSans, AppLanguage.RUSSIAN.nativeLabelFontFamily())
        assertSame(PixelSourceSans, AppLanguage.FRENCH.nativeLabelFontFamily())
        assertNull(AppLanguage.SYSTEM.nativeLabelFontFamily())
    }
}
