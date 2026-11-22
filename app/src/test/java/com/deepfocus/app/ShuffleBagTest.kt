package com.deepfocus.app

import com.deepfocus.app.pet.PetActionLibrary
import com.deepfocus.app.pet.PetActionTag
import com.deepfocus.app.pet.ShuffleBag
import com.deepfocus.app.pet.StageCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 发牌堆的测试
 *
 * 用户反馈：「我看还是经常能看到重复的 GIF，
 * 可以确保随机不放回的展示一轮后再重新开始吗」
 *
 * 所以规则很硬：**一轮之内每个候选只出现一次**。
 * 这条要是破了，用户就会一直看到重复的表情包。
 */
class ShuffleBagTest {

    @Test
    fun `一轮之内不重复`() {
        val candidates = (1..10).map { "a$it" }
        val bag = ShuffleBag(Random(42))
        val drawn = (1..10).map { bag.next(candidates)!! }
        assertEquals("一轮之内每个候选都该出现一次", candidates.toSet(), drawn.toSet())
    }

    @Test
    fun `一轮之后才开始重复`() {
        val candidates = (1..5).map { "a$it" }
        val bag = ShuffleBag(Random(7))
        val first = (1..5).map { bag.next(candidates)!! }
        assertEquals(candidates.toSet(), first.toSet())

        // 第 6 次才开始新一轮
        val sixth = bag.next(candidates)!!
        assertTrue("第 6 次抽到的应该还在候选里", candidates.contains(sixth))
    }

    @Test
    fun `候选为空时返回 null`() {
        val bag = ShuffleBag(Random(1))
        assertNull(bag.next(emptyList()))
    }

    @Test
    fun `候选变了会跳过失效的 id`() {
        val bag = ShuffleBag(Random(3))
        // 先用 5 个候选洗一堆
        repeat(2) { bag.next(listOf("a", "b", "c", "d", "e")) }

        // 候选缩到 2 个 —— 堆里失效的 id 要被跳过
        val drawn = mutableListOf<String>()
        repeat(6) {
            val id = bag.next(listOf("a", "b"))
            if (id != null) drawn.add(id)
        }
        assertTrue("只能抽到还在候选里的 id", drawn.all { it == "a" || it == "b" })
    }

    @Test
    fun `吃的动作一轮之内不重复`() {
        // 直接用真实的 FEED 动作集验一遍
        val feedIds = PetActionLibrary.ALL
            .filter { PetActionTag.FEED in it.tags }
            .map { it.id }
        assertTrue("FEED 动作太少，验不了", feedIds.size >= 8)

        val bag = ShuffleBag(Random(99))
        val drawn = feedIds.map { bag.next(feedIds)!! }
        assertEquals(
            "一轮喂食动作里每个吃的动作都该出现一次",
            feedIds.toSet(),
            drawn.toSet()
        )
    }

    @Test
    fun `能上台的吃的动作够开一轮`() {
        // 喂食只从能上台的动作里挑（舞台上的角色高度必须一致），
        // 所以上台的吃的动作要够多，一轮才转得开。
        val stageFeed = PetActionLibrary.ALL
            .filter { PetActionTag.FEED in it.tags }
            .filter { it.id in StageCatalog.STAGE_ACTIONS }
        assertTrue(
            "能上台的吃的动作只有 ${stageFeed.size} 个，太少",
            stageFeed.size >= 8
        )
    }
}
