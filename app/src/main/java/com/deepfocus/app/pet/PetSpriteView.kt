package com.deepfocus.app.pet

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.view.doOnLayout
import kotlin.math.roundToInt

/**
 * 桌宠本体视图
 *
 * 负责渲染大肥鱼并播放轻量动画。
 *
 * 动画清单（对齐 dsh-pet 的核心观感）：
 * - 待机呼吸：轻微的缩放起伏，2.2 秒一个周期
 * - 点击 Q 弹：scaleX 撑大 / scaleY 压扁 → 回弹（OvershootInterpolator）
 * - 行走：上下小跳 + 轻微左右倾
 * - 睡觉：呼吸幅度变大变慢 + 闭眼（切贴图）
 * - 朝向：拖动/行走时水平翻转
 *
 * P5 起接入了真实的手绘动画（assets/pet/anim 目录下的 animated WebP）：
 * - 有动画素材时直接播动画，不再用 Canvas 缩放模拟呼吸/行走
 * - 否则退回静态贴图 + Canvas 变换（老系统 API 26/27 没有 animated WebP 解码器）
 *
 * 刻意不做的事：不引入 Lottie / GLSurfaceView。
 * 桌宠只需要"会动"，不需要骨骼动画。用 Canvas 变换就够，
 * 省掉一个几 MB 的依赖，也省掉 alpha 视频解码的坑。
 */
class PetSpriteView @JvmOverloads constructor(
    context: Context,
    attrs: android.util.AttributeSet? = null
) : View(context, attrs) {

    companion object {
        /** 呼吸动画周期（ms） */
        const val BREATH_DURATION = 2200L

        /** Q 弹动画时长（ms） */
        const val SQUASH_DURATION = 320L

        /** 行走步频（ms） */
        const val WALK_CYCLE = 700L
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
    }
    private val matrix = Matrix()
    private val srcRect = Rect()
    private val dstRect = RectF()

    /** 当前静态贴图（动画的兜底） */
    private var bitmap: Bitmap? = null

    /** 当前姿态的动画贴图 */
    private var poseAnim: Drawable? = null

    /** 行走动画贴图 */
    private var walkAnim: Drawable? = null

    /** 实际正在播放的动画贴图 */
    private var activeAnim: Drawable? = null

    /**
     * 临时动作的动画贴图
     *
     * 互动玩法用：喂食、摸头、逗猫棒这些一次性动作，
     * 播完要回到当前姿态，不能把姿态本身改掉。
     */
    private var actionAnim: Drawable? = null

    /** 临时动作播放完的回调 */
    private var actionEndCallback: (() -> Unit)? = null

    /**
     * 播一个一次性动作
     *
     * 播完自动回到当前姿态 —— 这样「喂食」不会把桌宠永久变成吃饭的样子。
     *
     * @param action 要播的动作
     * @param onEnd 播完回调
     */
    fun playAction(action: PetAction, onEnd: (() -> Unit)? = null) {
        val drawable = PetActionLoader.loadAnimated(context, action)
        if (drawable == null) {
            onEnd?.invoke()
            return
        }
        actionEndCallback = onEnd
        actionAnim = drawable
        drawable.setCallback(this)
        (drawable as? Animatable)?.start()
        invalidate()
    }

    /** 停掉临时动作，回到当前姿态 */
    fun stopAction() {
        (actionAnim as? Animatable)?.stop()
        actionAnim?.setCallback(null)
        actionAnim = null
        actionEndCallback = null
        applyActiveAnim()
        invalidate()
    }

    /**
     * 当前姿态
     */
    var pose: PetSpriteLibrary.Pose = PetSpriteLibrary.Pose.IDLE
        set(value) {
            if (field != value) {
                field = value
                reloadSprites()
            }
        }

    /** 朝向：1 右，-1 左 */
    var facing: Int = 1
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    /** 是否正在睡觉（影响呼吸节奏） */
    var sleeping: Boolean = false

    /** 呼吸相位 0..1 */
    private var breathPhase = 0f

    /** Q 弹缩放因子 */
    private var squashX = 1f
    private var squashY = 1f

    /** 行走相位 */
    private var walkPhase = 0f

    /** 是否正在行走 */
    var walking: Boolean = false

    /** 呼吸动画 */
    private var breathAnimator: ValueAnimator? = null

    /** Q 弹动画 */
    private var squashAnimator: ValueAnimator? = null

    init {
        bitmap = PetSpriteLibrary.load(context, pose)
        poseAnim = PetSpriteLibrary.loadAnimated(context, pose)
        activeAnim = poseAnim
        startBreathing()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        (activeAnim as? Animatable)?.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        breathAnimator?.cancel()
        squashAnimator?.cancel()
        // 动画必须停掉，否则它会一直占着 Choreographer 的帧回调
        (activeAnim as? Animatable)?.stop()
        activeAnim?.setCallback(null)
    }

    /**
     * 重新加载当前姿态的素材（静态 + 动画）
     */
    private fun reloadSprites() {
        bitmap = PetSpriteLibrary.load(context, pose)
        poseAnim = PetSpriteLibrary.loadAnimated(context, pose)
        applyActiveAnim()
        invalidate()
    }

    /**
     * 切换"正在播哪个动画"
     *
     * 行走中播行走动画，否则播当前姿态的动画。
     * 切换时必须先停旧的再启动新的 —— AnimatedImageDrawable 同一时刻只能有一个在跑。
     */
    private fun applyActiveAnim() {
        val target = if (walking) walkAnim else poseAnim
        if (activeAnim === target) return
        (activeAnim as? Animatable)?.stop()
        activeAnim?.setCallback(null)
        activeAnim = target
        // setCallback 很关键：Drawable 靠 callback 通知 View 重绘，
        // 不设置的话动画不会刷新画面（会停在第一帧）
        target?.setCallback(this)
        (target as? Animatable)?.start()
    }

    /**
     * 启动呼吸动画
     */
    private fun startBreathing() {
        breathAnimator?.cancel()
        breathAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = BREATH_DURATION
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                breathPhase = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    /**
     * 点击时的 Q 弹反馈
     *
     * 形变方向：被点击瞬间横向撑开、纵向压扁 —— 果冻感。
     * 然后回弹到原状（略过冲）。
     */
    fun playSquash() {
        squashAnimator?.cancel()
        squashAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = SQUASH_DURATION
            interpolator = OvershootInterpolator(3f)
            addUpdateListener {
                val t = it.animatedValue as Float
                // 前 40% 压缩，后 60% 回弹
                val compression = if (t < 0.4f) {
                    t / 0.4f
                } else {
                    1f - (t - 0.4f) / 0.6f
                }
                squashX = 1f + compression * 0.22f
                squashY = 1f - compression * 0.18f
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    squashX = 1f
                    squashY = 1f
                    invalidate()
                }
            })
            start()
        }
    }

    /**
     * 设置行走状态
     */
    fun startWalking() {
        walking = true
        if (walkAnim == null) {
            walkAnim = PetSpriteLibrary.loadWalkAnimated(context)
        }
        applyActiveAnim()
        invalidate()
    }

    /** 停止行走 */
    fun stopWalking() {
        walking = false
        applyActiveAnim()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        // 临时动作优先于姿态 —— 互动玩法播一次性动作时，
        // 姿态的动画要让位。
        val anim = actionAnim ?: activeAnim
        val bmp = bitmap
        if (anim == null && bmp == null) return

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // 有动画素材时，动画本身已经包含呼吸/行走，
        // 再叠加 Canvas 缩放会变成"呼吸 + 呼吸"的鬼畜，所以这里关掉。
        val useAnim = anim != null
        val breathAmount = if (useAnim) 0f else if (sleeping) 0.055f else 0.022f
        val breathScale = 1f + kotlin.math.sin(breathPhase * 2f * Math.PI.toFloat()) * breathAmount

        // 行走：上下小跳
        var walkOffsetY = 0f
        var walkTilt = 0f
        if (walking && !useAnim) {
            walkPhase += 1f / WALK_CYCLE
            if (walkPhase > 1f) walkPhase -= 1f
            val swing = kotlin.math.sin(walkPhase * 2f * Math.PI.toFloat())
            walkOffsetY = -kotlin.math.abs(swing) * h * 0.045f
            walkTilt = swing * 4f
        }

        // 合成变换：缩放（呼吸 × Q 弹）→ 平移（行走）→ 旋转（行走倾斜）→ 翻转（朝向）
        val scaleX = breathScale * squashX * facing
        val scaleY = breathScale * squashY

        matrix.reset()
        // 以中心为锚点
        matrix.postTranslate(-w / 2f, -h / 2f)
        matrix.postScale(scaleX, scaleY)
        if (walkTilt != 0f) {
            matrix.postRotate(walkTilt)
        }
        matrix.postTranslate(w / 2f, h / 2f + walkOffsetY)

        canvas.save()
        // 注意：变换必须作用在 canvas 上。
        // 之前只算 matrix 却用 drawBitmap(dstRect) 绘制，导致行走的小跳与倾斜根本没画出来。
        canvas.concat(matrix)
        when {
            anim != null -> {
                val rect = fitRect(anim.intrinsicWidth, anim.intrinsicHeight, w, h)
                anim.setBounds(
                    rect.left.roundToInt(),
                    rect.top.roundToInt(),
                    rect.right.roundToInt(),
                    rect.bottom.roundToInt()
                )
                anim.draw(canvas)
            }
            bmp != null && !bmp.isRecycled -> {
                srcRect.set(0, 0, bmp.width, bmp.height)
                dstRect.set(fitRect(bmp.width, bmp.height, w, h))
                canvas.drawBitmap(bmp, srcRect, dstRect, paint)
            }
        }
        canvas.restore()
    }

    /**
     * 等比缩放并居中
     *
     * 素材（尤其横版 GIF）比例各不相同，直接铺满方形 View 会被拉宽。
     * 这里按 min(宽比, 高比) 缩到能完整放进 View，再居中。
     */
    private fun fitRect(srcW: Int, srcH: Int, dstW: Float, dstH: Float): RectF {
        if (srcW <= 0 || srcH <= 0) return RectF(0f, 0f, dstW, dstH)
        val scale = kotlin.math.min(dstW / srcW, dstH / srcH)
        val w = srcW * scale
        val h = srcH * scale
        val left = (dstW - w) / 2f
        val top = (dstH - h) / 2f
        return RectF(left, top, left + w, top + h)
    }
}
