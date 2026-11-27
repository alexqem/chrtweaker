package com.alexqem.chr.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [SessionEntity::class, SampleEntity::class], version = 1, exportSchema = false)
abstract class ChrDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun sampleDao(): SampleDao

    companion object {
        @Volatile
        private var INSTANCE: ChrDatabase? = null

        fun getDatabase(context: Context): ChrDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ChrDatabase::class.java,
                    "chr.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
