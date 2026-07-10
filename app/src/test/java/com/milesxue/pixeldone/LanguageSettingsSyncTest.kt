package com.milesxue.pixeldone

import com.milesxue.pixeldone.data.settings.InMemoryPixelDoneSettingsStore
import com.milesxue.pixeldone.data.sync.RemoteUserSettingsRecord
import com.milesxue.pixeldone.domain.sync.SyncRecordState
import com.milesxue.pixeldone.domain.todo.AppLanguage
import com.milesxue.pixeldone.domain.todo.DockConfig
import com.milesxue.pixeldone.domain.todo.DockPlusPlacement
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageSettingsSyncTest {
    @Test
    fun onlyLanguageMarksCloudSettingsDirty() = runTest {
        val store = InMemoryPixelDoneSettingsStore()
        store.applyRemoteSettings(
            RemoteUserSettingsRecord(languageMode = "system", updatedAtMillis = 100L, remoteVersion = 10L),
            syncedAtMillis = 100L,
        )

        store.saveDarkTheme(true)
        store.saveDockConfig(DockConfig(plusPlacement = DockPlusPlacement.LEFT_EDGE))
        assertEquals(SyncRecordState.SYNCED.name, store.loadSettingsForSync(200L).syncState)

        store.saveLanguage(AppLanguage.FRENCH)
        val dirty = store.loadSettingsForSync(300L)
        assertEquals("fr", dirty.languageMode)
        assertEquals(SyncRecordState.NOT_SYNCED.name, dirty.syncState)
        assertEquals(true, store.loadSettings().darkTheme)
        assertEquals(DockPlusPlacement.LEFT_EDGE, store.loadSettings().dockConfig.plusPlacement)
    }
}
