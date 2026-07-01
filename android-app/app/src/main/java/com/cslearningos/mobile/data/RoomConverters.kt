package com.cslearningos.mobile.data

import androidx.room.TypeConverter

class RoomConverters {
    @TypeConverter
    fun syncStatusToString(value: SyncStatus): String = value.name

    @TypeConverter
    fun stringToSyncStatus(value: String): SyncStatus = SyncStatus.valueOf(value)

    @TypeConverter
    fun quizSourceToString(value: QuizSource): String = value.name

    @TypeConverter
    fun stringToQuizSource(value: String): QuizSource = QuizSource.valueOf(value)

    @TypeConverter
    fun reviewResultToString(value: ReviewResult): String = value.name

    @TypeConverter
    fun stringToReviewResult(value: String): ReviewResult = ReviewResult.valueOf(value)
}
