package com.redline.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "motivational_videos")
data class MotivationalVideo(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val url: String,
    val type: String = "mp4", // "mp4" or "youtube"
    val thumbnailUrl: String? = null,
    val addedAt: Long = System.currentTimeMillis()
)
