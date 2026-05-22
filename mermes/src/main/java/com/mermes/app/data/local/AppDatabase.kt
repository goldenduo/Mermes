package com.mermes.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.mermes.app.data.local.dao.SessionDao
import com.mermes.app.data.local.dao.MessageDao
import com.mermes.app.data.local.dao.MemoryDao
import com.mermes.app.data.local.entity.SessionEntity
import com.mermes.app.data.local.entity.MessageEntity
import com.mermes.app.data.local.entity.MemoryEntity

@Database(
    entities = [
        SessionEntity::class,
        MessageEntity::class,
        MemoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun messageDao(): MessageDao
    abstract fun memoryDao(): MemoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mermes_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
