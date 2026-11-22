package com.deepfocus.app.ai

import com.deepfocus.app.data.FishOutfitStore
import com.deepfocus.app.data.local.dao.TravelJournalDao
import com.deepfocus.app.data.local.entity.TravelJournalEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 赛博旅游的执行器
 *
 * 把「跑一趟旅行 + 写进游历本 + 让她记住」这套流程收在一处，
 * 两个入口共用：
 * - 专注满 15 分钟自动触发（[com.deepfocus.app.ui.focus.FocusViewModel]）
 * - 调试时手动触发（游历本里的「现在就去」）
 *
 * 分开写两份的话，迟早会出现「自动触发会记事件、手动触发不记」这种偏差。
 */
@Singleton
class TravelService @Inject constructor(
    private val travelWorkflow: TravelWorkflow,
    private val travelJournalDao: TravelJournalDao,
    private val outfitStore: FishOutfitStore,
    private val fishBrain: FishBrain
) {

    /**
     * 现在出发，回来交一篇日记
     *
     * @return 写入游历本后的日记（带 id）
     */
    suspend fun travelNow(): TravelJournalEntity {
        val outfit = outfitStore.current()
        val visited = travelJournalDao.getVisitedDestinations()
        val persona = fishBrain.getPersona().let { "${it.name}，${it.traits.take(80)}" }

        val story = travelWorkflow.runTrip(
            outfit = outfit,
            visited = visited,
            persona = persona
        )

        val journal = TravelJournalEntity(
            title = story.title.ifBlank { story.destination },
            destination = story.destination,
            content = story.content,
            mood = story.mood.ifBlank { "还不错" },
            outfitId = outfit.id,
            // 她自己设计的「这一秒拍什么」也要落库 —— 生图时照它来
            photoIdea = story.photoIdea.ifBlank { null }
        )
        val id = travelJournalDao.insert(journal)

        // 她自己记住这趟经历（只是进上下文，不额外花 token）
        fishBrain.pushEvent(
            "你刚出门旅行回来——你去了${story.destination}，心情${journal.mood}，" +
                "还写了一篇日记。",
            "TRAVEL"
        )

        return journal.copy(id = id)
    }
}
