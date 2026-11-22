package com.deepfocus.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.deepfocus.app.data.local.entity.PersonaStateEntity
import kotlinx.coroutines.flow.Flow

/**
 * 人格状态 DAO（单条，id = 1）
 *
 * 存「用户自有字段 + 覆盖层 + 底稿选择」，见 [PersonaStateEntity]。
 */
@Dao
interface PersonaStateDao {

    @Query("SELECT * FROM persona_state WHERE id = 1")
    fun observe(): Flow<PersonaStateEntity?>

    @Query("SELECT * FROM persona_state WHERE id = 1")
    suspend fun get(): PersonaStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: PersonaStateEntity)
}
