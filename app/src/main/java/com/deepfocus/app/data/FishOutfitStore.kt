package com.deepfocus.app.data

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 装扮存储
 *
 * 只存一个 id —— 「她现在穿常服还是冬装」。
 *
 * 为什么不进 Room：装扮是两套固定形象 + 一个开关，
 * 不随养成变化、没有解锁、也不会增删。
 * 一个 SharedPreferences 键就够了，进数据库只是多一张表和一堆空列。
 *
 * 通过 StateFlow 广播，换装后所有读它的界面立刻跟着换 ——
 * 首页立绘、聊天初始界面、生图参考图都不用重启。
 */
@Singleton
class FishOutfitStore @Inject constructor(
    @ApplicationContext context: Context
) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _outfitId = MutableStateFlow(load())

    /** 当前装扮 id */
    val outfitId: StateFlow<String> = _outfitId.asStateFlow()

    /** 当前装扮（供 UI 订阅） */
    val outfit = _outfitId.map { FishOutfits.byId(it) }

    /** 当前装扮（同步读，生图等非 Compose 场景用） */
    fun current(): FishOutfit = FishOutfits.byId(_outfitId.value)

    /** 换装 */
    fun set(outfitId: String) {
        val safe = FishOutfits.byId(outfitId).id
        if (_outfitId.value == safe) return
        prefs.edit().putString(KEY_OUTFIT_ID, safe).apply()
        _outfitId.value = safe
    }

    private fun load(): String {
        val stored = prefs.getString(KEY_OUTFIT_ID, FishOutfits.NORMAL_ID)
        // 脏数据回落常服 —— 老版本可能存过别的 id
        return FishOutfits.byId(stored).id
    }

    companion object {
        private const val PREFS_NAME = "deep_focus_outfit"
        private const val KEY_OUTFIT_ID = "outfit_id"
    }
}
