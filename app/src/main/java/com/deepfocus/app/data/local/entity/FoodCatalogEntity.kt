package com.deepfocus.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 食物目录实体
 *
 * 食物**只有大白饭一种**，作用只有饱腹，**不涨好感度**（好感度只靠专注时长增长）。
 * 所以这里没有「好感度加成」和「掉落概率」字段 —— 单一食物用不上它们。
 */
@Entity(tableName = "food_catalog")
data class FoodCatalogEntity(
    /** 食物 ID，目前只有 "大白饭" */
    @PrimaryKey
    val foodId: String,
    /** 食物显示名称 */
    val displayName: String,
    /** 图标资源名 */
    val iconAsset: String
)
