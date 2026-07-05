package com.milesxue.pixeldone.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        TodoStateMetadataEntity::class,
        TodoChecklistEntity::class,
        TodoItemEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class PixelDoneDatabase : RoomDatabase() {
    abstract fun todoDao(): TodoDao
}
