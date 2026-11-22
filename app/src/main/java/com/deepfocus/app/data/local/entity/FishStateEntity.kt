package com.deepfocus.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 大肥鱼状态实体
 *
 * 承载「活的」那一部分：饱腹度、睡觉时长。
 * 好感度仍存在 user_fish 表（沿用 FocusCat 的养成语义），
 * 这里补的是好感度之外的「生理状态」。
 *
 * **没有「心情（mood）」维度** —— 情绪由好感度档位和当下情境推导，
 * 不单独存一个可正可负的数值（那会让「喂一次就心情+10」变成唯一互动方式）。
 *
 * **没有「精力（energy）」维度** —— 那是 FocusCat 猫模型的遗留，
 * 用户从没设计过它，也没有任何可见入口。睡觉改成「长时间没互动就睡」，
 * 不需要一个用户看不见也设不了的数值。
 *
 * 单条记录（id = 1），跟随当前激活的大肥鱼。
 */
@Entity(tableName = "fish_state")
data class FishStateEntity(
    @PrimaryKey
    val id: Int = 1,

    /** 饱腹度 0~100。吃大白饭会上升，随时间缓慢下降 */
    val satiety: Int = 70,

    /** 当前是否在睡觉 */
    val isSleeping: Boolean = false,

    /** 本次入睡时间戳 */
    val sleepStartedAt: Long = 0,

    /** 累计睡眠毫秒 */
    val totalSleepMillis: Long = 0,

    /** 上次喂食时间戳。「饿」的判定只认这个：距上次喂食超过 24 小时就是饿 */
    val lastFedAt: Long = 0,

    /** 上次主动行为触发时间戳（用于冷却判定） */
    val lastProactiveAt: Long = 0,

    /** 上次状态变化时间戳 */
    val updatedAt: Long = System.currentTimeMillis()
)
