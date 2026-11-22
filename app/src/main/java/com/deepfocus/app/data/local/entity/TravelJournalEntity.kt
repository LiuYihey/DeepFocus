package com.deepfocus.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 旅行日记
 *
 * 一次赛博旅游的产物 —— 一篇她用第一人称写的日记。
 *
 * 游历本是**共用**的：常服和冬装去的地方都进同一本日记，
 * [outfitId] 只记录「这趟她穿的是什么」，方便回看时知道那是谁。
 *
 * @param id 自增主键
 * @param title 日记标题（她起的）
 * @param destination 目的地
 * @param content 日记正文（第一人称，简洁）
 * @param mood 她的心情（开心 / 疲惫 / 惊喜……）
 * @param outfitId 这趟出行时的装扮 id
 * @param imagePath 配图的本地路径（可空 —— 配图要用户主动要）
 * @param imagePrompt 生图用的提示词（可空，方便重生成）
 * @param photoIdea 她自己设计的画面构想（自拍 / 他拍 / 漫画），可空
 * @param isFavorite 是否收藏
 * @param createdAt 写入时间戳
 */
@Entity(tableName = "travel_journals")
data class TravelJournalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val destination: String,
    val content: String,
    val mood: String,
    val outfitId: String,
    val imagePath: String? = null,
    val imagePrompt: String? = null,
    /** 旅行时她自己想的「这一秒拍什么」—— 生图时照它来，别另编一套 */
    val photoIdea: String? = null,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
