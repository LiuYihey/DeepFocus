package com.deepfocus.app.data

/**
 * 大肥鱼的装扮
 *
 * 只有两套固定形象 —— **常服** 与 **冬装**。没有第三套、没有解锁、没有自动切换。
 * 首次引导时选一套，之后可在设置里随时换。
 *
 * 装扮不只是换张图，它还牵引大肥鱼的**行为倾向**：
 * - 冬装 → 倾向去**冷**的地方（冰岛 / 雪国 / 北欧 / 高山）
 * - 常服 → 倾向去**暖**的地方（东南亚 / 地中海 / 岛屿）
 *
 * 同时它决定生图时用哪张**参考图** —— 参考图必须带，否则角色一致性就断了。
 *
 * 为什么不放数据库：装扮是「两套固定形象 + 一个开关」，
 * 没有解锁、没有用户自定义、也不会增删 —— 存进 Room 只会多一张表和一堆空列。
 * 放代码里当常量，UI 侧直接读。
 *
 * @param id 持久化用的 id
 * @param displayName 显示名
 * @param description 一句话介绍
 * @param portraitAssetPath 静态立绘资源路径（assets 下）
 * @param temperature 温度倾向 —— 决定旅行目的地往冷还是往暖走
 * @param hasAnimatedVariant 有没有配套的桌宠动画
 * @param referenceAssetPath 生图参考图路径（默认就是立绘；冬装立绘去了白底，
 *        参考图仍用用户指定的 `winter.jpg`，因为参考图需要完整的画面信息）
 */
data class FishOutfit(
    val id: String,
    val displayName: String,
    val description: String,
    val portraitAssetPath: String,
    val temperature: FishOutfitTemperature,
    val hasAnimatedVariant: Boolean,
    val referenceAssetPath: String = portraitAssetPath
)

/**
 * 装扮的温度倾向
 *
 * 牵引赛博旅游的目的地选取：冬装往冷的地方跑，常服往暖的地方跑。
 */
enum class FishOutfitTemperature {
    /** 偏冷 —— 冰岛、雪国、北欧、高山 */
    COLD,

    /** 偏暖 —— 东南亚、地中海、岛屿 */
    WARM
}

/**
 * 全部装扮
 *
 * 只有常服和冬装两套。加新装扮要同时想清楚：
 * 立绘资源放哪、有没有配套动画、往冷还是往暖牵引。
 */
object FishOutfits {

    /** 常服 id */
    const val NORMAL_ID = "normal"

    /** 冬装 id */
    const val WINTER_ID = "winter"

    /** 常服 —— 默认装扮 */
    val NORMAL = FishOutfit(
        id = NORMAL_ID,
        displayName = "常服",
        description = "它最常穿的样子，走哪儿都合适",
        portraitAssetPath = "fish/bigfatfish.png",
        temperature = FishOutfitTemperature.WARM,
        hasAnimatedVariant = true
    )

    /** 冬装 —— 有配套立绘，但没有冬装版桌宠动画 */
    val WINTER = FishOutfit(
        id = WINTER_ID,
        displayName = "冬装",
        description = "裹着厚外套和毛线帽，适合去冷的地方",
        // 立绘用去白底的 PNG（放在浅色首页上不会出现一块白方），
        // 生图参考图仍用原图 winter.jpg。
        portraitAssetPath = "fish/winter_cutout.png",
        temperature = FishOutfitTemperature.COLD,
        hasAnimatedVariant = false,
        referenceAssetPath = "fish/winter.jpg"
    )

    /** 全部装扮，按展示顺序 */
    val ALL: List<FishOutfit> = listOf(NORMAL, WINTER)

    /** 按 id 取装扮，取不到就回落常服 */
    fun byId(id: String?): FishOutfit = ALL.firstOrNull { it.id == id } ?: NORMAL
}
