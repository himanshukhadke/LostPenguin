package com.lostpenguin.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ReelStat::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun reelStatDao(): ReelStatDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lostpenguin.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
