package com.gothwad.indogram.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [OfflineDraft::class, NotificationItem::class],
    version = 1,
    exportSchema = false
)
abstract class IndogramDatabase : RoomDatabase() {
    abstract fun indogramDao(): IndogramDao

    companion object {
        @Volatile
        private var INSTANCE: IndogramDatabase? = null

        fun getDatabase(context: Context): IndogramDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    IndogramDatabase::class.java,
                    "indogram_database"
                )
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
