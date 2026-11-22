package com.deepfocus.app.pet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable

/**
 * 桌宠素材库
 *
 * 素材在构建前用 `.tools/install_art.py` 抠过白底，
 * 存在 assets/pet/ 下（WebP，带 alpha）。
 *
 * 与 App 内的表情包（`assets/stickers/`）不同：
 * - stickers/ 是给聊天气泡用的表情包，白底、无 alpha
 * - pet/ 是抠过白底的紧凑版，给桌宠用（桌宠尺寸小，需要更干净的边缘）
 *
 * P5 起新增了动画层 `assets/pet/anim/`：
 * - 有动画时优先播动画，没有（老系统 / 素材缺失）则退回静态贴图
 *
 * 动画素材来自 dsh-pet 的大肥鱼桌宠（一百余个透明动作）：
 * https://github.com/PC2005-cloud/dsh-pet
 * 许可：代码 MIT；素材禁止商用、二创须署名。非商业用途可用。
 * 由 `tools/media/install_pet_animations.py` 从 GIF 转码为 animated WebP。
 */
object PetSpriteLibrary {

    /** 动画素材目录 */
    private const val ANIM_DIR = "pet/anim"

    /**
     * 桌宠的动画状态
     *
     * **每个姿态都指向全量动作库里的一个具体动作**（见 [PetActionLibrary]），
     * 所以姿态和互动玩法用的是同一批素材 —— 不再有两套重复的文件。
     *
     * @param actionId 对应的动作 id
     */
    enum class Pose(val actionId: String) {
        /** 待机 */
        IDLE("daiji-huxi-xiuxian"),

        /** 饿了，端着大白饭 */
        HUNGRY("chi-baifan"),

        /** 睡着了 */
        SLEEPING("shuijiao-xunhuan"),

        /** 摸鱼 */
        SLACKING("suisuinian-cazhuo-suisuinian"),

        /** 开心 */
        EXCITED("dianji-huiying-kaixin-yuedong"),

        /** 委屈 */
        SAD("beixiayitiao-zhamao"),

        /** 生气 */
        ANGRY("dianji-huiying-aojiao-shengqi-ceshen-zhanshi"),

        /** 加油 */
        CHEER("yuandi-qiaoji-zhuomian-hudong"),

        /** 害羞 */
        SHY("dianji-huiying-haixiu-jingya"),

        /** 得意 */
        PROUD("qian-dai-man-yi"),

        /** 无聊 */
        BORED("dai-kong-ru-xi"),

        /** 在读书 */
        READING("shu-jin-zhou-mei"),

        /** 思考 */
        THINKING("gongzuozhuangtai-sikao-maopao"),

        /** 正在忙（工作状态） */
        WORKING("gongzuozhuangtai-manglu-dianan"),

        /** 庆祝（完成一次专注） */
        CELEBRATE("fang-yanhua"),

        /** 被拎起来（拖拽反馈） */
        DRAG("beishubiao-tuozhuai-xuankong-fankui")
    }

    /** 行走动画对应的动作 id（走路不是姿态，是移动中的临时动作） */
    const val WALK_ACTION_ID = "pangxie-zoulu"

    /** 缓存：避免每帧重新解码 */
    private val cache = HashMap<String, Bitmap>()

    /** 动画贴图缓存（AnimatedImageDrawable 有播放状态，不能随便丢弃） */
    private val animCache = HashMap<String, Drawable?>()

    /**
     * 加载桌宠贴图（静态）
     *
     * 走动作库里的静态兜底图（`{序号}_{id}_still.webp`），
     * 不再单独维护一套 `pet/{pose}.webp`。
     */
    fun load(context: Context, pose: Pose): Bitmap? {
        val action = PetActionLibrary.byId(pose.actionId) ?: return null
        val path = PetActionLibrary.stillPath(action)
        cache[path]?.let { if (!it.isRecycled) return it }
        return try {
            context.assets.open(path).use {
                BitmapFactory.decodeStream(it)
            }?.also { cache[path] = it }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 加载桌宠动画贴图
     *
     * 返回 null 表示"这台设备/这个姿态没有动画"，调用方退回静态贴图。
     */
    fun loadAnimated(context: Context, pose: Pose): Drawable? {
        val action = PetActionLibrary.byId(pose.actionId) ?: return null
        return loadAnimFile(context, PetActionLibrary.assetPath(action))
    }

    /**
     * 加载行走动画
     *
     * 行走不是一种"姿态"，是移动过程中的临时动作，
     * 所以单独提供入口，不占用 Pose 枚举。
     */
    fun loadWalkAnimated(context: Context): Drawable? {
        val action = PetActionLibrary.byId(WALK_ACTION_ID) ?: return null
        return loadAnimFile(context, PetActionLibrary.assetPath(action))
    }

    private fun loadAnimFile(context: Context, path: String): Drawable? {
        if (!PetAnimDecoder.supported) return null
        if (animCache.containsKey(path)) return animCache[path]
        val d = PetAnimDecoder.decode(context, path)
        animCache[path] = d
        return d
    }

    /**
     * 释放缓存（桌宠服务销毁时）
     */
    fun clear() {
        cache.values.forEach { if (!it.isRecycled) it.recycle() }
        cache.clear()
        animCache.clear()
    }
}
