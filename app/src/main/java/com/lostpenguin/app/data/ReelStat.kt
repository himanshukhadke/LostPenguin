package com.lostpenguin.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reel_stats")
data class ReelStat(
    @PrimaryKey val date: String,  // "yyyy-MM-dd"
    val count: Int
)
