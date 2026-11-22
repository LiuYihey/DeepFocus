package com.deepfocus.app.pet

import kotlin.random.Random

/**
 * 发牌堆（shuffle bag）—— 随机但**一轮之内不重复**
 *
 * 用户反馈：「我看还是经常能看到重复的 GIF，
 * 可以确保随机不放回的展示一轮后再重新开始吗」
 *
 * 做法：把全部候选**洗牌**成一叠，从上往下取；
 * 取完一整轮（每个都播过了）才重新洗牌。
 *
 * 新一轮洗牌时会把**上一轮最后一个**挪到最前面，
 * 避免「刚播完的立刻又播」。
 *
 * 纯逻辑，不碰 Android —— 可以直接跑 JVM 单元测试。
 */
class ShuffleBag(private val random: Random = Random.Default) {

    /** 当前这叠牌（从队首取） */
    private val bag = ArrayDeque<String>()

    /** 上一轮最后一个取的 id —— 新一轮洗牌时挪到最前 */
    private var lastId: String? = null

    /**
     * 取下一个 id
     *
     * @param candidates 候选 id 列表
     * @return 抽到的 id；候选为空时返回 null
     */
    fun next(candidates: List<String>): String? {
        if (candidates.isEmpty()) return null

        // 堆空了 → 重新洗牌
        if (bag.isEmpty()) {
            val shuffled = candidates.shuffled(random).toMutableList()
            val last = lastId
            if (last != null && shuffled.size > 1) {
                val head = shuffled.firstOrNull { it == last }
                if (head != null) {
                    shuffled.remove(head)
                    shuffled.add(0, head)
                }
            }
            bag.addAll(shuffled)
        }

        // 候选变了（素材被删等）→ 堆里可能有已失效的 id，跳过
        while (bag.isNotEmpty()) {
            val id = bag.removeFirst()
            if (id in candidates) {
                lastId = id
                return id
            }
        }
        // 兜底：极端情况下（候选全变）直接从候选里挑
        return candidates.random(random)
    }

    /** 这叠牌里还剩几张 */
    fun remaining(): Int = bag.size
}
