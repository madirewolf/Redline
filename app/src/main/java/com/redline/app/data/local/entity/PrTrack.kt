package com.redline.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pr_tracks")
data class PrTrack(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val label: String,
    val trackUri: String,
    val dropTimestampMs: Long = 0,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
