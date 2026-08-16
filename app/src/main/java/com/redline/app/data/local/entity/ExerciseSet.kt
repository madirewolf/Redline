package com.redline.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "exercise_sets",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class ExerciseSet(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    val exerciseName: String,
    val canonicalExerciseKey: String? = null,
    val weight: Float? = null,
    val unit: String = "lbs",
    val reps: Int? = null,
    val rpe: Float? = null,
    val notes: String? = null,
    val tempoAverageRepSeconds: Float? = null,
    val tempoDegradationPercent: Float? = null,
    val failureProximityScore: Int? = null,
    val rawVoiceInput: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
