package com.lostpenguin.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ReelStatDao {

    @Query("SELECT * FROM reel_stats ORDER BY date DESC")
    fun getAllStats(): Flow<List<ReelStat>>

    @Query("SELECT * FROM reel_stats WHERE date = :date LIMIT 1")
    suspend fun getStatForDate(date: String): ReelStat?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stat: ReelStat)

    @Query("SELECT SUM(count) FROM reel_stats")
    fun getTotalCount(): Flow<Int?>

    @Query("SELECT * FROM reel_stats ORDER BY date DESC LIMIT 7")
    fun getLast7Days(): Flow<List<ReelStat>>
}
