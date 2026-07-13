package com.milesxue.pixeldone

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRulesTest {
    @Test
    fun rebuildableSyncDatabaseIsExcludedFromEveryBackupMode() {
        val extraction = resourceFile("data_extraction_rules.xml").readText()
        val legacy = resourceFile("backup_rules.xml").readText()

        assertTrue(extraction.contains("<cloud-backup>"))
        assertTrue(extraction.contains("<device-transfer>"))
        assertEquals(6, SyncDatabaseFiles.sumOf { name -> extraction.countDatabaseExcludes(name) })
        assertEquals(3, SyncDatabaseFiles.sumOf { name -> legacy.countDatabaseExcludes(name) })
        assertTrue(!extraction.contains("pixel_done_local.db"))
        assertTrue(!legacy.contains("pixel_done_local.db"))
    }

    private fun resourceFile(name: String): File = sequenceOf(
        File("src/main/res/xml/$name"),
        File("app/src/main/res/xml/$name"),
    ).first { it.isFile }

    private fun String.countDatabaseExcludes(name: String): Int =
        Regex("<exclude\\s+domain=\"database\"\\s+path=\"${Regex.escape(name)}\"\\s*/>")
            .findAll(this)
            .count()

    private companion object {
        val SyncDatabaseFiles = listOf(
            "pixel_done_sync.db",
            "pixel_done_sync.db-shm",
            "pixel_done_sync.db-wal",
        )
    }
}
