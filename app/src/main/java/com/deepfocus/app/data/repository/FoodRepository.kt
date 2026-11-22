package com.deepfocus.app.data.repository

import com.deepfocus.app.data.local.dao.FoodCatalogDao
import com.deepfocus.app.data.local.dao.FoodInventoryDao
import com.deepfocus.app.data.local.entity.FoodCatalogEntity
import com.deepfocus.app.data.local.entity.FoodInventoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * 食物仓库
 * 封装食物目录、库存的数据访问和大白饭发放逻辑
 */
class FoodRepository(
    private val foodFishCatalogDao: FoodCatalogDao,
    private val foodInventoryDao: FoodInventoryDao
) {

    /** 获取所有食物类型 */
    suspend fun getAllFoods(): List<FoodCatalogEntity> = foodFishCatalogDao.getAllFoods()

    /** 根据食物 ID 获取食物信息 */
    suspend fun getFoodById(foodId: String): FoodCatalogEntity? = foodFishCatalogDao.getById(foodId)

    /** 观察食物库存（Flow） */
    fun observeInventory(): Flow<List<FoodInventoryEntity>> = foodInventoryDao.observeAllInventory()

    /** 获取所有库存（单次查询） */
    suspend fun getInventory(): List<FoodInventoryEntity> = foodInventoryDao.getAllInventory()

    /** 获取指定食物的库存数量 */
    suspend fun getFoodCount(foodId: String): Int =
        foodInventoryDao.getByFoodId(foodId)?.count ?: 0

    /** 增加食物数量 */
    suspend fun addFood(foodId: String, amount: Int = 1) {
        foodInventoryDao.addFood(foodId, amount, System.currentTimeMillis())
    }

    /** 消耗一个食物 */
    suspend fun consumeOne(foodId: String) {
        foodInventoryDao.consumeOne(foodId, System.currentTimeMillis())
    }

    /**
     * 发放一份大白饭
     *
     * 食物只有大白饭一种，没有随机成分 ——
     * 专注满 30 分钟兑换 1 份，直接进背包库存。
     *
     * 库存上限 [MAX_INVENTORY] 份，满了就不再加（专注时长照常换好感度）。
     *
     * @return 获得的大白饭；目录为空（异常）时返回 null
     */
    suspend fun grantBigRice(): FoodCatalogEntity? {
        val rice = foodFishCatalogDao.getById(FOOD_BIG_RICE)
            ?: foodFishCatalogDao.getAllFoods().firstOrNull()
            ?: return null
        val current = foodInventoryDao.getByFoodId(rice.foodId)?.count ?: 0
        if (current >= MAX_INVENTORY) return rice
        addFood(rice.foodId, 1)
        return rice
    }

    companion object {
        /** 大白饭的食物 ID */
        const val FOOD_BIG_RICE = "大白饭"

        /** 背包库存上限 */
        const val MAX_INVENTORY = 5
    }
}
