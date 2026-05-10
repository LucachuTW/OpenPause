package io.github.openpause.data.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [AppRuleEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class OpenPauseDatabase : RoomDatabase() {
    abstract fun appRuleDao(): AppRuleDao
}
