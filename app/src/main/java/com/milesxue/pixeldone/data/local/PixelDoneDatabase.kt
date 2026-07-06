package com.milesxue.pixeldone.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TodoStateMetadataEntity::class,
        TodoChecklistEntity::class,
        TodoItemEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class PixelDoneDatabase : RoomDatabase() {
    abstract fun todoDao(): TodoDao
}

internal object PixelDoneMigrations {
    val Migration1To2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE todo_items ADD COLUMN locallyPurgedAtMillis INTEGER")
        }
    }
}
