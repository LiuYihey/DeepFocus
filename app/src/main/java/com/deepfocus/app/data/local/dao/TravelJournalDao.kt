package com.deepfocus.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.deepfocus.app.data.local.entity.TravelJournalEntity
import kotlinx.coroutines.flow.Flow

/**
 * 旅行日记 DAO
 *
 * 游历本的数据源。
 */
@Dao
interface TravelJournalDao {

    /** 观察全部日记（新的在前） */
    @Query("SELECT * FROM travel_journals ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TravelJournalEntity>>

    /** 观察收藏的日记 */
    @Query("SELECT * FROM travel_journals WHERE isFavorite = 1 ORDER BY createdAt DESC")
    fun observeFavorites(): Flow<List<TravelJournalEntity>>

    /** 取全部日记（单次） */
    @Query("SELECT * FROM travel_journals ORDER BY createdAt DESC")
    suspend fun getAll(): List<TravelJournalEntity>

    /** 取最近一条日记 */
    @Query("SELECT * FROM travel_journals ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatest(): TravelJournalEntity?

    /** 按 id 取日记 */
    @Query("SELECT * FROM travel_journals WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TravelJournalEntity?

    /** 已经去过的目的地（去重，按时间倒序）—— 目的地 Agent 用它避开重复 */
    @Query("SELECT DISTINCT destination FROM travel_journals ORDER BY createdAt DESC")
    suspend fun getVisitedDestinations(): List<String>

    /** 写入一篇日记 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(journal: TravelJournalEntity): Long

    /** 回填配图路径 */
    @Query("UPDATE travel_journals SET imagePath = :imagePath, imagePrompt = :imagePrompt WHERE id = :id")
    suspend fun updateImage(id: Long, imagePath: String?, imagePrompt: String?)

    /** 收藏 / 取消收藏 */
    @Query("UPDATE travel_journals SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    /** 删一篇 */
    @Query("DELETE FROM travel_journals WHERE id = :id")
    suspend fun delete(id: Long)
}
