package com.milesxue.pixeldone

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.milesxue.pixeldone.domain.sync.AuthSession
import com.milesxue.pixeldone.domain.sync.SyncCoordinatorStatus
import com.milesxue.pixeldone.domain.sync.SyncRunState
import com.milesxue.pixeldone.ui.theme.PixelDoneTheme
import com.milesxue.pixeldone.ui.todo.AuthInputState
import com.milesxue.pixeldone.ui.todo.PasswordChangeState
import com.milesxue.pixeldone.ui.todo.SettingsCloudPanel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsCloudPanelTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun syncStatusIsSingleAndChangePasswordSharesTheCloudTextEdge() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val accountText = context.getString(R.string.account)
        val syncText = context.getString(R.string.sync)
        val changePasswordText = context.getString(R.string.change_password)
        composeRule.setContent {
            PixelDoneTheme {
                SettingsCloudPanel(
                    authSession = AuthSession(
                        signedIn = true,
                        userId = "user-1",
                        userEmail = "person@example.com",
                        displayLabel = "person@example.com",
                        cloudAvailable = true,
                        accessToken = "token",
                    ),
                    authInput = AuthInputState(),
                    passwordChangeState = PasswordChangeState(),
                    syncStatusText = "Stable",
                    syncStatus = SyncCoordinatorStatus.STABLE,
                    syncRunState = SyncRunState(status = SyncCoordinatorStatus.STABLE),
                    onOpenCloudSignIn = {},
                    onSignOut = {},
                    onSyncNow = {},
                    onChangePassword = { _, _, _ -> },
                )
            }
        }

        assertEquals(
            1,
            composeRule.onAllNodesWithText(syncText, useUnmergedTree = true).fetchSemanticsNodes().size,
        )
        val accountLeft = composeRule.onNodeWithText(accountText, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.left
        val syncLeft = composeRule.onNodeWithText(syncText, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.left
        val changePasswordLeft = composeRule.onNodeWithText(changePasswordText, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.left

        assertEquals(accountLeft, syncLeft, 0.5f)
        assertEquals(accountLeft, changePasswordLeft, 0.5f)
    }
}
