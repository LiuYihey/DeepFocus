package com.deepfocus.app.ui.theme

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/**
 * 动效规格（高定规范 9）
 *
 * ── 弹簧换算 ────────────────────────────────────────────────
 * CSS `spring(stiffness, damping)` → Compose `spring(dampingRatio, stiffness)`：
 *
 * ```
 * dampingRatio = damping / (2 * sqrt(stiffness * mass))   // mass = 1
 * ```
 *
 * - **拖拽 1200/80 零回弹**
 *   ratio = 80 / (2 * √1200) ≈ 1.155 ≥ 1 → 过阻尼，**零回弹**，
 *   拖动跟手、到位即停，不会有橡皮筋式的来回晃。
 *   Compose 的 dampingRatio 上限是 1，用 `DampingRatioNoBouncy` 落地。
 *
 * - **点赞 380/18 微弹**
 *   ratio = 18 / (2 * √380) ≈ 0.462 → 欠阻尼，**轻微过冲**，
 *   给"被夸了"一个短促的弹一下，是好感度反馈的灵魂。
 */
object DeepFocusMotion {

    /**
     * 拖拽 / 位移类动效 —— 零回弹
     *
     * 过阻尼（ratio > 1）在 Compose 里等价于临界阻尼（ratio = 1）：
     * 有速度感，但绝不越界回弹。
     */
    val ZeroRebound: SpringSpec<Float> = spring(
        dampingRatio = 1f,
        stiffness = 1200f
    )

    /**
     * 点赞 / 好感度反馈 —— 微弹
     *
     * ratio ≈ 0.46，欠阻尼一点点：落位时轻轻过冲一下即回稳。
     */
    val MicroBounce: SpringSpec<Float> = spring(
        dampingRatio = 0.46f,
        stiffness = 380f
    )
}
