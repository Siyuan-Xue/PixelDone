package com.milesxue.pixeldone

import androidx.compose.ui.graphics.Color
import com.milesxue.pixeldone.domain.sync.SyncCoordinatorStatus
import com.milesxue.pixeldone.ui.theme.PixelDonePalette
import com.milesxue.pixeldone.ui.todo.settingsValueColor
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncStatusPresentationTest {
    @Test
    fun settingsValueColorUsesStateSemantics() {
        val colors = PixelDonePalette(
            background = Color(0xFF000001),
            surface = Color(0xFF000002),
            surfaceSoft = Color(0xFF000003),
            surfaceRaised = Color(0xFF000004),
            selectedSurface = Color(0xFF000005),
            completedSurface = Color(0xFF000006),
            destructiveSurface = Color(0xFF000007),
            border = Color(0xFF000008),
            borderWeak = Color(0xFF000009),
            textPrimary = Color(0xFF00000A),
            textSecondary = Color(0xFF00000B),
            primary = Color(0xFF00000C),
            primaryInteractive = Color(0xFF00000D),
            success = Color(0xFF00000E),
            error = Color(0xFF00000F),
            disabledSurface = Color(0xFF000010),
            disabledText = Color(0xFF000011),
            onPrimary = Color(0xFF000012),
        )

        assertEquals(colors.error, SyncCoordinatorStatus.ERROR.settingsValueColor(colors))
        assertEquals(colors.success, SyncCoordinatorStatus.SYNCED.settingsValueColor(colors))
        assertEquals(colors.textSecondary, SyncCoordinatorStatus.SYNCING.settingsValueColor(colors))
        assertEquals(colors.textSecondary, SyncCoordinatorStatus.SIGNED_OUT.settingsValueColor(colors))
    }
}