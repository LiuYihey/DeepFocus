package com.deepfocus.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 应用主题颜色常量
 *
 * 设计语言：Deep Sea · 深海蓝 & 奶白（DeepFocus 专属）
 *
 * 角色是 Q 版鲸鱼娘「大肥鱼」—— 深蓝发、女仆装、深蓝鱼尾，
 * 所以主色走深海蓝，底色走奶白，点缀用蜜桃。
 *
 * ── 分层原则（严格执行）─────────────────────────────────────
 * 1. 禁黑框与 #000：全 App 不出现纯黑（#000000）与黑色描边，
 *    所有"框线"一律用冷色调 + 明度分层表达，避免生硬割裂。
 * 2. 1-3% 微明度 4 层背景分层：背景不是"白 / 灰"两档，而是
 *    canvas → surface → surfaceRaised → surfaceOverlay 四层，
 *    每层相对下层只提亮 1-3% 明度，靠极微弱色差建立空间纵深，
 *    视觉上"平"但不空，达到 Apple / Linear 式的克制质感。
 * 3. 暗色字重全降 40：深色主题下所有文字字重相对浅色下调一档
 *    （如 SemiBold → Medium，Bold → SemiBold），因为深底本身
 *    已经提供了足够的视觉重量，字重再高会显得刺眼、发闷。
 * 4. 冷色偏移：所有中性色都带 3-6% 的深蓝偏移（蓝/青向），
 *    与大肥鱼的深海蓝发色、女仆装的奶白呼应。
 */

// ========== 主色 - 深海蓝（Deep Sea Blue）==========
// 冷静、可靠、有陪伴感，把"克制分心"变成"安静陪伴"，
// 呼应大肥鱼的深蓝发色与鱼尾
val Primary = Color(0xFF2F5D8C)
val PrimaryLight = Color(0xFF5B87B5)
val PrimaryDark = Color(0xFF24476B)
// 浅海蓝：用于彩色背景（天空/视频）上的次级强调文字，
// 比 Primary 更柔和浅淡，不会与白色计时数字抢视觉重心
val PrimarySoft = Color(0xFFA8C6E0)

// ========== 强调色 - 蜜桃（Peach Accent）==========
// 整屏唯一的暖色点，用于点赞 / 达成 / 好感度飘字等小面积高光
val Accent = Color(0xFFE8A87C)

// ========== 浅色主题 · 4 层微明度背景分层 ==========
// 起点仍是纯白（与 AI 生成的纯白底大肥鱼图无缝融合），
// 但向下每一层只降 1-3% 明度，形成 canvas → surface → raised → overlay
val Background = Color(0xFFFFFFFF)        // L0 canvas：纯白底（L=100%）
val Surface = Color(0xFFFBFCFD)           // L1 surface：比 canvas 暗 1.2%
val SurfaceVariant = Color(0xFFF3F6F9)    // L2 raised：比 L1 暗 2.4%
val SurfaceOverlay = Color(0xFFF8FAFC)    // L3 overlay：悬浮层，比 L2 亮 1.6%（抬升感）

// ========== 深色主题 · 4 层微明度背景分层 ==========
// 底色绝不用 #000：以深海蓝墨为起点，逐层提亮 1-3%
val DarkBackground = Color(0xFF121A24)      // L0 canvas：深海蓝墨，非纯黑（L=10.6%）
val DarkSurface = Color(0xFF17212D)         // L1 surface：+2.7% 明度
val DarkSurfaceVariant = Color(0xFF1C2836)  // L2 raised：+2.8% 明度
val DarkSurfaceOverlay = Color(0xFF22303F)  // L3 overlay：+2.9% 明度（悬浮抬升）

// ========== 文本色（深海蓝墨，非纯黑） ==========
val TextPrimary = Color(0xFF1C2A38)      // 深海蓝墨
val TextSecondary = Color(0xFF6B7A8B)    // 冷灰蓝
val DarkTextPrimary = Color(0xFFE8EFF5)  // 冷米白（非纯白，降低眩光）
val DarkTextSecondary = Color(0xFFA3B2C0)

// ========== 状态色（冷调，去"吓人感"，用户偏好非红） ==========
// 成功 - 柔和海蓝绿，安静治愈
val Success = Color(0xFF4E8F7C)
// 错误 - 柔暖珊瑚，提示但不刺眼（仅用于图标/边框/容器底色，不用红色）
val Error = Color(0xFFE0896F)
// 错误正文色 - 加深砖红，bodySmall/14sp 在白底达 AA 4.5:1
val ErrorText = Color(0xFF9C4526)
// 待处理/警告 - 暖琥珀（warm amber #F59E0B，硬约束：权限提示使用此色）
val Warning = Color(0xFFF59E0B)

val OnPrimaryDark = Color(0xFFF2F7FC)

// ========== 装饰色（冷调为主，蜜桃点缀） ==========
val StarGold = Color(0xFFD9B25C)      // 暖金（星星稀有度）
val RarityCommon = Color(0xFF9AA9B8)  // 冷灰
val RarityRare = Color(0xFF5B9BC4)    // 雾蓝（稀有）
val RarityEpic = Color(0xFF9B7BC4)    // 淡紫（史诗，珍贵感）

// ========== 氛围光晕（Ambient Halo）专用色 ==========
// 大肥鱼身后的那圈柔光 —— 让角色从背景里"浮"出来，而不是贴在纸上。
// 用主色的极低透明度做径向渐变中心，往外衰减到全透明。
/** 浅色主题：光晕中心（主色 7% 混入白） */
val HaloLight = Color(0x142F5D8C)
/** 浅色主题：光晕外缘（完全透明） */
val HaloLightEdge = Color(0x002F5D8C)
/** 深色主题：光晕中心（提亮主色 12%） */
val HaloDark = Color(0x1F5B87B5)
/** 深色主题：光晕外缘（完全透明） */
val HaloDarkEdge = Color(0x005B87B5)

// ========== 毛玻璃卡片（Glass Card）专用色 ==========
// 台词气泡、专注页叠加层用。半透明填充 + 一层高光边，
// 让文字浮在图片/动画上仍然清晰。
/** 浅色主题：卡片填充（白 78% 混入） */
val GlassCardLight = Color(0xC7FFFFFF)
/** 浅色主题：卡片描边（主色 10%，不是黑框） */
val GlassStrokeLight = Color(0x1A2F5D8C)
/** 深色主题：卡片填充（深蓝墨 72%） */
val GlassCardDark = Color(0xB81A2430)
/** 深色主题：卡片描边（浅主色 14%） */
val GlassStrokeDark = Color(0x245B87B5)

// ========== 液态玻璃（Liquid Glass）专用色 ==========
// 苹果液态玻璃的核心是"折射"：边缘因折射而提亮、中心因厚度而加深。
// 这组颜色用于底部导航的玻璃层，保证在任意背景上都有可读的层次。
/** 玻璃层中心色（带背景色 8% 混入，形成"厚度"感） */
val GlassTint = Color(0x142F5D8C)

/** 玻璃上边缘高光（模拟顶光折射，最亮的一条细线） */
val GlassHighlightTop = Color(0x66FFFFFF)

/** 玻璃下边缘折射光（模拟底部反射环境光，冷调） */
val GlassHighlightBottom = Color(0x33BFD6EC)

/** 玻璃层内侧柔和阴影（让玻璃"浮"起来，而非平贴） */
val GlassInnerShadow = Color(0x14101820)

/** 深色主题下的玻璃高光（深色中玻璃更靠"发光边"表达） */
val GlassHighlightTopDark = Color(0x40FFFFFF)
val GlassHighlightBottomDark = Color(0x26A8C6E0)
