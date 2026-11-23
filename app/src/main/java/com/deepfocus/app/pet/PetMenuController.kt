package com.deepfocus.app.pet

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.core.view.doOnLayout
import com.deepfocus.app.R
import kotlin.math.roundToInt

/**
 * 桌宠长按菜单
 *
 * 对标 dsh-pet 的「动作点播菜单」（分类折叠 + 一堆动作任播）。
 * DeepFocus 版做得更克制 —— 菜单项就是真正有用的那几个，
 * 不塞 100+ 个花哨动作（桌宠用户研究的第一条教训：
 * 「91 段动画里用户只会记住 3-5 个」）。
 *
 * 菜单项：
 * - 睡觉 / 醒来
 * - 回到原位
 * - 换个方向
 * - 藏起来 / 唤回
 * - 关闭桌宠
 */
class PetMenuController(
    private val context: Context,
    private val listener: Listener
) {

    interface Listener {
        /** 切换睡觉状态 */
        fun onMenuToggleSleep()

        /** 逗它玩：随机播一个动作 */
        fun onMenuPlay()

        /** 回到默认位置 */
        fun onMenuGoHome()

        /** 翻转朝向 */
        fun onMenuFlip()

        /** 切换隐藏 */
        fun onMenuToggleVisible()

        /** 关闭桌宠 */
        fun onMenuQuit()
    }

    private var popup: PopupWindow? = null

    /**
     * 显示菜单
     *
     * @param anchor 已挂在 WindowManager 上的桌宠窗口 View（PopupWindow 需要它的 window token）
     * @param anchorX 屏幕坐标 X
     * @param anchorY 屏幕坐标 Y
     */
    @SuppressLint("InflateParams")
    fun show(anchor: View, anchorX: Int, anchorY: Int) {
        dismiss()

        val root = LayoutInflater.from(context).inflate(R.layout.pet_menu, null)
        val window = PopupWindow(
            root,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            // 注意：PopupWindow 的 background 在不同 API 上有 setBackground /
            // setBackgroundDrawable 两个入口，用显式方法名避开 Kotlin 的属性歧义。
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            isFocusable = true
            elevation = 12f * context.resources.displayMetrics.density
        }

        root.findViewById<View>(R.id.menuSleep).setOnClickListener {
            listener.onMenuToggleSleep()
            dismiss()
        }
        root.findViewById<View>(R.id.menuPlay).setOnClickListener {
            listener.onMenuPlay()
            dismiss()
        }
        root.findViewById<View>(R.id.menuHome).setOnClickListener {
            listener.onMenuGoHome()
            dismiss()
        }
        root.findViewById<View>(R.id.menuFlip).setOnClickListener {
            listener.onMenuFlip()
            dismiss()
        }
        root.findViewById<View>(R.id.menuHide).setOnClickListener {
            listener.onMenuToggleVisible()
            dismiss()
        }
        root.findViewById<View>(R.id.menuQuit).setOnClickListener {
            listener.onMenuQuit()
            dismiss()
        }

        popup = window
        // 锚点定位到桌宠附近，注意 PopupWindow 用的是绝对屏幕坐标。
        // 第一个参数必须传**已经挂在窗口上**的 View —— 服务里没有 Activity，
        // 传 null token 会直接抛 BadTokenException 把整个进程带崩。
        window.showAtLocation(
            anchor,
            Gravity.NO_GRAVITY,
            (anchorX - dp(60)).coerceAtLeast(0),
            (anchorY - dp(180)).coerceAtLeast(0)
        )
    }
    /**
     * 关闭菜单
     */
    fun dismiss() {
        popup?.dismiss()
        popup = null
    }

    /**
     * 是否正在显示
     */
    val isShowing: Boolean get() = popup?.isShowing == true

    private fun dp(v: Int): Int =
        (v * context.resources.displayMetrics.density).roundToInt()
}
