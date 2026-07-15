package com.milesxue.pixeldone

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.milesxue.pixeldone.domain.sync.AuthSession
import com.milesxue.pixeldone.domain.sync.SyncCoordinatorStatus
import com.milesxue.pixeldone.domain.sync.SyncRunState
import com.milesxue.pixeldone.ui.theme.PixelDoneTheme
import com.milesxue.pixeldone.ui.todo.AuthInputState
import com.milesxue.pixeldone.ui.todo.PasswordChangeState
import com.milesxue.pixeldone.ui.todo.PasswordChangeEditorPanel
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
        var passwordEditorOpenRequests = 0
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
                    onOpenPasswordChange = { passwordEditorOpenRequests += 1 },
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
        composeRule.onNodeWithText(changePasswordText, useUnmergedTree = true).performClick()
        composeRule.runOnIdle {
            assertEquals(1, passwordEditorOpenRequests)
        }
    }

    @Test
    fun passwordChangeEditorUsesTheSharedBottomEditorFields() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val currentPasswordText = context.getString(R.string.current_password)
        val newPasswordText = context.getString(R.string.new_password)
        val confirmationText = context.getString(R.string.confirm_new_password)
        composeRule.setContent {
            PixelDoneTheme {
                PasswordChangeEditorPanel(
                    state = PasswordChangeState(),
                    onSubmit = { _, _, _ -> },
                    onCancel = {},
                    compactForKeyboard = false,
                )
            }
        }

        composeRule.onNodeWithText(currentPasswordText, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText(newPasswordText, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText(confirmationText, useUnmergedTree = true).assertExists()
    }
}
