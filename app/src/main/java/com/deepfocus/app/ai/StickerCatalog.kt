package com.deepfocus.app.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * 表情包目录（语义化清单）
 *
 * 学自 dsh-pet 的表情包机制：
 * https://github.com/PC2005-cloud/dsh-pet
 *
 * 上游的做法是「图片名 + 内容描述」一一对应，把整张表交给模型，
 * 让模型按当前语境挑一张 —— 而不是靠关键词猜。
 *
 * 本类照搬这个思路，但用**本地真实素材**：
 * `assets/stickers/` 共 58 张，分两类：
 * - `st_{情绪}_{01..04}.webp` —— 47 张带文字梗的小图（来自「蓝色大肥鱼档案馆」）
 * - `st_{情绪}_big.webp` —— 11 张无文字的统一规格大立绘（原 `assets/expressions/`）
 *
 * **表情包与大表情已统一**：一个目录、一份清单、一条加载路径。
 * 动作库（`assets/pet/anim/all/`）是另一回事 —— 那是桌宠和首页大肥鱼的表演素材。
 *
 * 三条机制的区别（上游也是这么分的）：
 * - **对话配图**：模型读完整份清单，按语境自己选 —— [catalogForPrompt]
 * - **碎碎念配图**：同样把清单交给模型，她自己决定这次配不配图
 * - **动画联动**：模型回一个动作标记，桌宠当场演出来（桌宠侧已实现）
 *
 * 2026-11-22 起**没有随机兜底、没有关键词猜测**：
 * 用户反馈「感觉是随机固定发送的，agent 没有阅读这个表情包本身是什么内容，
 * 就随便发一个糊弄」。现在模型读清单 → 自己判断 → 用或不用，
 * 不用就真的没有表情包。
 *
 * 关键约束：**模型只能从清单里选**。清单外的表情包它拿不到、
 * 也编不出来 —— 这是「宁缺毋滥」在表情包上的体现。
 *
 * @param id 资源文件名（不含扩展名），与 assets/stickers/ 下文件一一对应
 * @param desc 内容描述。写给模型看，要能说明「什么语境该用这张」
 */
data class Sticker(
    val id: String,
    val desc: String
)

object StickerCatalog {

    /**
     * 全部表情包，按情绪分组。
     *
     * 顺序即优先级：模型看到清单时前面的更容易被选中。
     * 常用情绪放前面。
     */
    val ALL: List<Sticker> = listOf(
        // ---- 吐槽 / 犯困：日常最常用 ----
        Sticker("st_sleeping_01", "缩成一团打呼噜说「睡头大肥鱼」，适合犯困、睡着、待机"),
        Sticker("st_sleeping_02", "躺床上说「总觉得好像忘记了什么事情」，适合犯迷糊、忘事、睡懵了"),
        Sticker("st_sleeping_03", "趴着打呼噜，头上飘着 Zzz，适合睡着、休息、安静陪着"),
        Sticker("st_sleeping_04", "翻白眼躺在水里喊「死掉了」，适合累瘫、彻底没电、装死"),

        Sticker("st_thinking_01", "翻白眼吐舌头说「已思考（用时13秒）我不知道耶」，适合无语、吐槽、答不上来"),
        Sticker("st_thinking_02", "瞪大眼睛头顶感叹号「!!」，适合震惊、没想到、被吓一跳"),
        Sticker("st_thinking_03", "歪头头顶问号，无文字，适合疑问、困惑、追问"),
        Sticker("st_thinking_04", "托腮认真说「让我想想」，适合认真、斟酌、思考"),

        // ---- 开心 / 鼓励 ----
        Sticker("st_excited_01", "原地起飞喊「原地起飞」，适合特别开心、成功了、惊喜"),
        Sticker("st_excited_02", "张开双手扑过来喊「小肥鱼太棒了！」，适合夸奖、庆祝、想被表扬"),
        Sticker("st_excited_03", "双手捂脸笑得眯起眼，无文字，适合开心、害羞地笑、被逗乐"),
        Sticker("st_excited_04", "雪地里开心得起飞说「雪地起飞」，适合冬天、雪天、开心"),

        Sticker("st_cheer_01", "举双手欢呼「冲冲冲中！！」，适合鼓劲、加油、开干"),
        Sticker("st_cheer_02", "敬礼说「誓死捍卫深度求索」，适合表忠心、认真、干活"),
        Sticker("st_cheer_03", "比耶手势说「推雪人快乐」，适合冬天、雪地、玩雪"),
        Sticker("st_cheer_04", "举着牌子说「我们的模型 正在蒸蒸日上哦」，适合介绍自己、认真表态"),

        // ---- 饿 / 吃 ----
        Sticker("st_hungry_01", "饿得变形眼巴巴说「饿到变形」，适合饿、想吃大白饭"),
        Sticker("st_hungry_02", "头顶写着「又饿了」，适合饿、催饭、想吃东西"),
        Sticker("st_hungry_03", "端着大白饭眼睛发亮，适合开饭、投喂成功、吃饭"),
        Sticker("st_hungry_04", "抱着半个西瓜啃得满脸汁，适合吃货、吃西瓜、馋"),

        // ---- 摸鱼 / 摆烂 ----
        Sticker("st_slacking_01", "比心配「摸鱼」，适合摸鱼、偷懒、划水"),
        Sticker("st_slacking_02", "趴在桌上甩尾巴说「摸鱼中」，适合上班摸鱼、休息一下"),
        Sticker("st_slacking_03", "趴在雪地上打哈欠，适合冬天瘫着、什么都不想干"),
        Sticker("st_slacking_04", "拍桌狂笑说「我喜欢偷懒!」，适合摆烂、理直气壮偷懒"),

        // ---- 委屈 / 生气 ----
        Sticker("st_sad_01", "可怜巴巴说「小鱼卒业了」，适合难过、委屈、失败"),
        Sticker("st_sad_02", "低头说「我是路边鱼」，适合失落、自怜、被忽略"),
        Sticker("st_sad_03", "抹眼泪说「冻成冰块」，适合冷、难受、被打败"),
        Sticker("st_sad_04", "抹眼泪说「不要…再擦了…」，适合被批评、委屈求饶"),

        Sticker("st_angry_01", "眉头紧皱握拳，气鼓鼓，适合生气、愤怒、不爽"),
        Sticker("st_angry_02", "生气地说「气死你!」，适合发飙、骂人、气不过"),
        Sticker("st_angry_03", "暴跳如雷喊「竟然敢违反AI的指令！」，适合被气到、发飙、暴走"),
        Sticker("st_angry_04", "叉腰怒吼「哈?」，适合震惊、被气到、质问"),

        // ---- 得意 / 温柔 ----
        Sticker("st_proud_01", "睁大眼举着水母花，无文字，适合安静、专注、欣赏"),
        Sticker("st_proud_02", "闭眼微笑，很满足的样子，适合满足、惬意、舒服"),
        Sticker("st_proud_03", "举牌子「好模型」，适合得意、被夸奖、骄傲"),
        Sticker("st_proud_04", "指着镜头说「老板娘最美」，适合甜、彩虹屁、夸用户"),

        Sticker("st_shy_01", "捂脸说「别看我!」，适合害羞、不好意思"),
        Sticker("st_shy_02", "围巾捂脸说「围巾捂脸」，适合害羞、捂脸、不好意思"),
        Sticker("st_shy_03", "脸红低头说「脸好烫」，适合害羞、心动、不好意思"),
        Sticker("st_shy_04", "缩成一团背过身只看得见尾巴，适合躲、藏、不好意思"),

        // ---- 无聊 ----
        Sticker("st_bored_01", "面无表情特写，无文字，适合无语、随便、懒得理"),
        Sticker("st_bored_03", "举手说「无事发生」，适合什么都没发生、平静、划水"),
        Sticker("st_bored_04", "举起牌子问「你的知识库截止日期是？」，适合质疑、追问、吐槽模型"),

        // ---- 读书 / 学习 ----
        Sticker("st_reading_01", "举着牌子「不是…而是…大学习」，适合认真、知识、讲解"),
        Sticker("st_reading_02", "捧书说「低碳的鲸子! 统统不用抓鲸去!」，适合念书、科普"),
        Sticker("st_reading_03", "端坐读书配「正在训练用户」，适合学习、专注、陪读"),
        Sticker("st_reading_04", "捧着书皱眉说「鲸鱼的鲸是啥鲸阿，我要去研究研究」，适合好奇、探究"),

        // ---- 大表情（无文字的统一规格大立绘）----
        // 原先放在 assets/expressions/ 单独一套，现已并入贴纸池。
        // 命名 `st_{情绪}_big`，跟 `st_{情绪}_{01..04}` 区分开：
        // 前者是无文字立绘，后者是带文字梗的小图。
        // 清单顺序在后 —— 它们是「保底」用的，不是首选。
        Sticker("st_angry_big", "大表情·生气，眉头紧皱握拳气鼓鼓，无文字"),
        Sticker("st_bored_big", "大表情·无聊，撇嘴面无表情，无文字"),
        Sticker("st_cheer_big", "大表情·加油，举起小拳头给你打气，无文字"),
        Sticker("st_excited_big", "大表情·兴奋，举起双手欢呼，无文字"),
        Sticker("st_hungry_big", "大表情·饿，端着一碗大白饭，无文字"),
        Sticker("st_proud_big", "大表情·得意，叉腰抬头很神气，无文字"),
        Sticker("st_reading_big", "大表情·读书，戴着眼镜捧书看，无文字"),
        Sticker("st_sad_big", "大表情·委屈，眼眶含泪低头，无文字"),
        Sticker("st_shy_big", "大表情·害羞，脸颊泛红低头，无文字"),
        Sticker("st_sleeping_big", "大表情·睡觉，闭眼蜷着打呼噜，无文字"),
        Sticker("st_thinking_big", "大表情·思考，歪头托腮想事情，无文字"),
    )

    /** id → 描述 */
    private val BY_ID: Map<String, Sticker> = ALL.associateBy { it.id }

    /**
     * 给模型看的清单文本
     *
     * 上游用「前 N 张」限制 token 消耗（每条消息都要附这份清单）。
     * 这里默认给**全部** 58 张 —— 58 条描述约 2k 字符，比上游 27 张还多，
     * 属于可接受范围；真要省就传 [limit]。
     *
     * 每条描述写的是**这张图里画了什么**（画面 + 文字），
     * 模型据此判断「这句话配哪张」—— 这就是「先读表情包含义再决定发不发」。
     *
     * @param limit 最多附几张，0 表示不限。前 limit 张优先（写在前面的更容易被选中）
     */
    fun catalogForPrompt(limit: Int = 0): String {
        val list = if (limit > 0) ALL.take(limit) else ALL
        return list.joinToString("\n") { "- ${it.id}：${it.desc}" }
    }

    /**
     * 同一情绪的「大表情」备选
     *
     * 模型选的那张贴纸取不到时（比如文件缺失），退到同情绪的无文字大立绘 ——
     * 情绪对得上，比随便换一张别的情绪强。
     *
     * @return 找不到返回 null
     */
    fun bigFallback(sticker: Sticker): Sticker? {
        val emotion = sticker.id.substringAfter("st_").substringBefore("_")
        if (emotion.isEmpty()) return null
        return BY_ID["st_${emotion}_big"]
    }

    /** 按 id 查 */
    fun byId(id: String): Sticker? = BY_ID[id]

    /**
     * 加载表情包 bitmap
     *
     * @param seed 同一情绪有多张时，决定取哪一张（保证同一消息稳定）
     */
    fun load(context: Context, sticker: Sticker, seed: Int = 0): Bitmap? {
        return try {
            context.assets.open("stickers/${sticker.id}.webp").use {
                BitmapFactory.decodeStream(it)
            }
        } catch (e: Exception) {
            null
        }
    }

    /** 表情包是否存在 */
    fun exists(context: Context, sticker: Sticker): Boolean {
        return try {
            context.assets.open("stickers/${sticker.id}.webp").close()
            true
        } catch (e: Exception) {
            false
        }
    }
}
