package com.redline.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "calorie_entries")
data class CalorieEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val foodName: String,
    val calories: Float,
    val protein: Float? = null,
    val carbs: Float? = null,
    val fat: Float? = null,
    val servingSize: Float? = null,
    val unit: String = "g",
    val timestamp: Long = System.currentTimeMillis(),
    val rawVoiceInput: String? = null
)
