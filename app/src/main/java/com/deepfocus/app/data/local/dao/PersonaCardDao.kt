package com.deepfocus.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.deepfocus.app.data.local.entity.PersonaCardEntity
import kotlinx.coroutines.flow.Flow

/**
 * 自定义人格卡 DAO
 *
 * 只管自定义包；内置包是静态资源（`assets/personas/builtin_cards.json`），不落库。
 */
@Dao
interface PersonaCardDao {

    @Query("SELECT * FROM persona_cards ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PersonaCardEntity>>

    @Query("SELECT * FROM persona_cards ORDER BY createdAt DESC")
    suspend fun getAll(): List<PersonaCardEntity>

    @Query("SELECT * FROM persona_cards WHERE id = :id")
    suspend fun getById(id: String): PersonaCardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(card: PersonaCardEntity)

    @Delete
    suspend fun delete(card: PersonaCardEntity)

    @Query("DELETE FROM persona_cards WHERE id = :id")
    suspend fun deleteById(id: String)
}
