package com.cslearningos.mobile.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        LearningNodeEntity::class,
        QuizItemEntity::class,
        ReviewStateEntity::class,
        ReviewAttemptEntity::class,
        NodeFtsEntity::class,
        QuizFtsEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(RoomConverters::class)
abstract class LearningDatabase : RoomDatabase() {
    abstract fun learningDao(): LearningDao

    companion object {
        fun create(context: Context): LearningDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                LearningDatabase::class.java,
                "learning-os.db"
            ).build()
    }
}
