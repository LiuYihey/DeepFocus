package com.deepfocus.app.data

import com.deepfocus.app.data.local.entity.AffinityAchievementEntity
import com.deepfocus.app.data.local.entity.FishCatalogEntity
import com.deepfocus.app.data.local.entity.FoodCatalogEntity

/**
 * 种子数据对象
 * 用于应用首次启动时初始化大肥鱼形态、食物目录、成就
 */
object SeedData {

    /**
     * 获取大肥鱼形态种子数据
     *
     * 只有一条鱼：经典大肥鱼。装扮系统已整块删除，catalog 里就这一条。
     */
    fun getSeedFishBreeds(): List<FishCatalogEntity> {
        return listOf(
            FishCatalogEntity(
                breedId = "bigfatfish",
                displayName = "经典",
                description = "蓝眼温柔，粘人治愈，专注路上的最佳搭档",
                iconAsset = "fish_bigfat",
                idleAnimAsset = "fish_bigfat_idle",
                eatAnimAsset = "fish_bigfat_eat",
                happyAnimAsset = "fish_bigfat_happy"
            )
        )
    }

    /**
     * 获取食物目录种子数据
     *
     * **只有大白饭一种** —— 食物的作用只有饱腹，不涨好感度。
     * 专注 30 分钟兑换 1 份，进背包库存（上限 5 份）。
     * foodId 与 assets/foods 下的图片文件名一致。
     */
    fun getSeedFoods(): List<FoodCatalogEntity> {
        return listOf(
            FoodCatalogEntity(
                foodId = "大白饭",
                displayName = "大白饭",
                iconAsset = "food_bigrice"
            )
        )
    }

    /**
     * 获取好感度成就种子数据
     *
     * 里程碑 = 好感度点数（不是投喂次数）。
     * 好感度由「喂食 / 专注 / 聊天」共同推高，所以成就判定也按好感度来，
     * 用户光靠专注也能一步步解锁。
     * 档位与 [com.deepfocus.app.ai.FishMoodResolver] 的好感度边界保持一致。
     */
    fun getSeedAchievements(): List<AffinityAchievementEntity> {
        return listOf(
            AffinityAchievementEntity(
                milestone = 10,
                title = "初识之友",
                description = "好感度 10，大肥鱼开始信任你"
            ),
            AffinityAchievementEntity(
                milestone = 50,
                title = "默契伙伴",
                description = "好感度 50，大肥鱼与你心意相通"
            ),
            AffinityAchievementEntity(
                milestone = 100,
                title = "心有灵犀",
                description = "好感度 100，它开始黏你了，说话都不一样了"
            ),
            AffinityAchievementEntity(
                milestone = 200,
                title = "同一条鱼",
                description = "好感度 200，你们已经不用多说就懂了"
            ),
            AffinityAchievementEntity(
                milestone = 350,
                title = "离不开你",
                description = "好感度 350，它开始黏着你不放，见不到就要念叨"
            )
        )
    }
}
