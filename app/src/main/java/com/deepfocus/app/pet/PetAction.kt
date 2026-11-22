package com.deepfocus.app.pet

/**
 * 大肥鱼动作库（全量 109 个）
 *
 * 素材来自 DeepSeek 生态的 dsh-pet：
 * https://github.com/PC2005-cloud/dsh-pet
 * 许可：代码 MIT；**素材禁止商用**，二创在公开渠道须署名。
 *
 * 转码说明：`tools/media/install_all_actions.py`
 * 把 GIF 转成 animated WebP（`assets/pet/anim/all/{序号}_{拼音名}.webp`），
 * 体积约为 GIF 的 1/3，且渐变不会脏。
 *
 * ---
 *
 * ## 互动玩法的设计依据
 *
 * 这批动作不是「随便挑几个循环播」—— 每个动作都自带**道具和场景元素**：
 * 雪人、风筝、孔明灯、烟花、陀螺、扑克、画笔、红包、火锅、贝壳、
 * 围巾、气球、小鱼干、砖头…… 这些元素就是玩法的原料。
 *
 * 把它们按「玩家做了什么」归类，玩法就自然长出来了：
 *
 * | 玩法 | 触发 | 用到的动作 |
 * |---|---|---|
 * | 投喂 | 用户喂大白饭 | 各种吃的（吃白饭/西瓜/年糕/汤圆…） |
 * | 摸头 | 用户长按/轻点 | 撸毛、伸懒腰、抖毛、呆空如隙 |
 * | 逗猫棒 | 用户快速拖动 | 追泡泡、追陀螺、逗猫棒、三球抛接 |
 * | 送礼 | 好感度里程碑 | 收红包、拆礼物、扑克魔术 |
 * | 陪伴 | 长时间不互动 | 看书、思考、发呆、睡觉 |
 * | 节日 | 特定日期/季节 | 堆雪人、火锅、端午粽子、中秋月饼、重阳糕 |
 * | 心情 | 大肥鱼状态 | 傲娇、害羞、生气、开心、得意 |
 * | 恶作剧 | 随机惊喜 | 放烟花、孔明灯、放河灯、放风筝、凭空生花 |
 * | 甩飞 | 用户用力拖动 | 被拽悬空、翻转、旋转 |
 *
 * @param id 稳定标识（同时是资源文件名前缀）
 * @param index 在动作库里的序号（001-109），对应素材文件名前缀
 * @param desc 画面描述。给模型看，也是玩法的判断依据
 * @param tags 玩法标签，同一动作可以属于多个玩法
 */
data class PetAction(
    val id: String,
    val index: Int,
    val desc: String,
    val tags: Set<String>
)

/**
 * 动作玩法标签
 *
 * 桌宠按标签挑动作，比按姿态硬编码灵活得多 ——
 * 新增素材只要打上标签，玩法自动覆盖。
 */
object PetActionTag {
    /** 投喂 —— 用户给了吃的 */
    const val FEED = "feed"

    /** 摸头 —— 用户的亲近接触 */
    const val PET = "pet"

    /** 逗猫棒 —— 需要动起来的玩具 */
    const val PLAY = "play"

    /** 送礼 —— 收到东西 */
    const val GIFT = "gift"

    /** 陪伴 —— 安静待着 */
    const val IDLE = "idle"

    /** 睡觉 */
    const val SLEEP = "sleep"

    /** 节日 —— 特定时令 */
    const val FESTIVE = "festive"

    /** 心情 —— 情绪表达 */
    const val MOOD = "mood"

    /** 恶作剧 / 惊喜 —— 自己搞事 */
    const val TRICK = "trick"

    /** 工作 —— 专注相关 */
    const val WORK = "work"

    /** 思考 —— 「正在深度思考中」是核心玩法，单独一个标签 */
    const val THINK = "think"

    /** 被甩 —— 用户用力拖动的反馈 */
    const val THROWN = "thrown"

    /** 登场 —— 有出场感的 */
    const val ENTER = "enter"

    /** 社交 —— 对话相关 */
    const val TALK = "talk"
}

/**
 * 109 个动作的完整清单
 *
 * 描述全部对着画面逐个核对过 —— 描述错了，模型就会选错图。
 * 命名统一用 `{序号}_{拼音}`，和资源文件一一对应。
 */
object PetActionLibrary {

    /** 全量动作 */
    val ALL: List<PetAction> = listOf(
        // ---------- 001-016：吃与日常 ----------
        PetAction("beiluoye-yanmo", 1, "眼膜被撕掉，睁大眼睛愣住", setOf(PetActionTag.PET)),
        PetAction("beishubiao-tuozhuai-xuankong-fankui", 2, "被拎起来悬在半空，四肢乱蹬", setOf(PetActionTag.THROWN)),
        PetAction("beixiayitiao-zhamao", 3, "被吓一跳，抓住自己的头发", setOf(PetActionTag.MOOD)),
        PetAction("bian-gezi", 4, "低头编格子，专注做手工", setOf(PetActionTag.IDLE, PetActionTag.WORK)),
        PetAction("cha-zhuyu-shangju", 5, "捧着手机刷知乎，眉头紧锁", setOf(PetActionTag.IDLE)),
        PetAction("chai-liwu", 6, "拆开礼物，星星从里面蹦出来", setOf(PetActionTag.GIFT)),
        PetAction("chaoda-shenlanyao", 7, "双手高举伸一个超大懒腰", setOf(PetActionTag.PET, PetActionTag.IDLE)),
        PetAction("chenjian-shuaya", 8, "早晨刷牙，一边刷一边晃", setOf(PetActionTag.IDLE)),
        PetAction("chi-baifan", 9, "抱着一大碗白饭猛吃", setOf(PetActionTag.FEED)),
        PetAction("chi-bingqilin-ronghua", 10, "抱着冰麒麟冰块，边缘在融化", setOf(PetActionTag.FESTIVE, PetActionTag.FEED)),
        PetAction("chi-changshoumian", 11, "抱着超长的手擀面拉扯着吃", setOf(PetActionTag.FESTIVE, PetActionTag.FEED)),
        PetAction("chi-chongyanggao", 12, "小口啃重阳糕", setOf(PetActionTag.FESTIVE, PetActionTag.FEED)),
        PetAction("chi-dazhaxie", 13, "举着大虾和大蟹一起啃", setOf(PetActionTag.FESTIVE, PetActionTag.FEED)),
        PetAction("chi-labazhou", 14, "端着一碗粥呼噜呼噜喝", setOf(PetActionTag.FESTIVE, PetActionTag.FEED)),
        PetAction("chi-niangao", 15, "捧着年糕大口咬", setOf(PetActionTag.FESTIVE, PetActionTag.FEED)),
        PetAction("chi-qingtuan", 16, "举着一枚青团小心咬", setOf(PetActionTag.FESTIVE, PetActionTag.FEED)),

        // ---------- 017-032：更多吃的 + 器乐 + 待机 ----------
        PetAction("chi-tanghulu", 17, "举着一串糖葫芦啃", setOf(PetActionTag.FESTIVE, PetActionTag.FEED)),
        PetAction("chi-token", 18, "举着一枚硬币端详", setOf(PetActionTag.MOOD)),
        PetAction("chi-wancan", 19, "端着饭碗认真吃", setOf(PetActionTag.FEED)),
        PetAction("chi-wucan", 20, "捧着午餐盒吃饭", setOf(PetActionTag.FESTIVE, PetActionTag.FEED)),
        PetAction("chi-xigua", 21, "抱着半个西瓜啃得满脸汁", setOf(PetActionTag.FESTIVE, PetActionTag.FEED)),
        PetAction("chi-zaocan", 22, "端着早茶慢慢吃", setOf(PetActionTag.FESTIVE, PetActionTag.FEED)),
        PetAction("chi-zongzi", 23, "捧着一只粽子剥", setOf(PetActionTag.FESTIVE, PetActionTag.FEED)),
        PetAction("chijiaozi", 24, "捧着饺子蘸醋吃", setOf(PetActionTag.FESTIVE, PetActionTag.FEED)),
        PetAction("chitangyuan", 25, "举着一颗汤圆，小口咬开", setOf(PetActionTag.FESTIVE, PetActionTag.FEED)),
        PetAction("chou-tuoluo", 26, "抽陀螺，陀螺在地上转", setOf(PetActionTag.PLAY)),
        PetAction("chuanzhenqiqiao", 27, "抬头看天上的一弯月亮", setOf(PetActionTag.IDLE, PetActionTag.TRICK)),
        PetAction("chui-dizi", 28, "吹笛子，音符飘出来", setOf(PetActionTag.PLAY, PetActionTag.IDLE)),
        PetAction("chui-qiqiu", 29, "吹泡泡，腮帮子鼓起来", setOf(PetActionTag.PLAY)),
        PetAction("da-keshui-bei-jingxing", 30, "睡得正香突然被惊醒", setOf(PetActionTag.SLEEP, PetActionTag.MOOD)),
        PetAction("dai-kong-ru-xi", 31, "睁着眼发呆，一动不动", setOf(PetActionTag.IDLE)),
        PetAction("daiji-huxi-xiuxian", 32, "待机呼吸，身体轻轻起伏", setOf(PetActionTag.IDLE)),

        // ---------- 033-048：情绪回应 + 户外 ----------
        PetAction("dakou-chi-lingshi", 33, "抓起零食大口往嘴里塞", setOf(PetActionTag.FESTIVE, PetActionTag.FEED)),
        PetAction("dangqiuqian", 34, "用钳子夹住一个球", setOf(PetActionTag.PLAY)),
        PetAction("dianji-huiying-aojiao-shengqi-ceshen-zhanshi", 35, "傲娇地侧身，生气地撅嘴", setOf(PetActionTag.MOOD)),
        PetAction("dianji-huiying-haixiu-jingya", 36, "睁大眼睛，惊讶地往后缩", setOf(PetActionTag.MOOD)),
        PetAction("dianji-huiying-kaixin-yuedong", 37, "开心地蹦起来，整个人都在弹", setOf(PetActionTag.MOOD)),
        PetAction("dianji-huiying-naoyang-gegexiao", 38, "恼怒地咯咯笑", setOf(PetActionTag.MOOD)),
        PetAction("dianji-huiying-yuanqi-huishou", 39, "元气满满地蹦跳", setOf(PetActionTag.MOOD)),
        PetAction("dongwu-huanrao", 40, "被小动物环绕", setOf(PetActionTag.TRICK, PetActionTag.IDLE)),
        PetAction("dongzhangxiwang", 41, "东张西望，脑袋转来转去", setOf(PetActionTag.IDLE)),
        PetAction("duixueren", 42, "滚进雪堆，堆成一个雪人", setOf(PetActionTag.FESTIVE, PetActionTag.ENTER)),
        PetAction("fang-fengzheng", 43, "放风筝，风筝飞上天", setOf(PetActionTag.TRICK)),
        PetAction("fang-kongmingdeng", 44, "双手举起孔明灯放上天", setOf(PetActionTag.TRICK)),
        PetAction("fang-yanhua", 45, "仰头看，烟花在头顶炸开", setOf(PetActionTag.TRICK, PetActionTag.MOOD)),
        PetAction("fanghedeng", 46, "放河灯，灯光飘在水面上", setOf(PetActionTag.TRICK)),
        PetAction("fen-wen-bu-sheng", 47, "脸上浮起一抹粉色", setOf(PetActionTag.MOOD)),
        PetAction("gongzuozhuangtai-chuitou-tanqi-maohan", 48, "垂头丧气地叹气", setOf(PetActionTag.MOOD, PetActionTag.WORK)),

        // ---------- 049-064：工作状态 + 社交 ----------
        PetAction("gongzuozhuangtai-manglu-dianan", 49, "埋头忙碌，手一直没停", setOf(PetActionTag.WORK)),
        PetAction("gongzuozhuangtai-qingdian-guidang", 50, "低头整理归档，一件件归位", setOf(PetActionTag.WORK)),
        PetAction("gongzuozhuangtai-queyue-qingzhu", 51, "雀跃地轻轻起舞", setOf(PetActionTag.MOOD)),
        PetAction("gongzuozhuangtai-sikao-maopao", 52, "托腮思考，头顶冒出思考的气泡", setOf(PetActionTag.WORK, PetActionTag.THINK)),
        PetAction("gongzuozhuangtai-yuandi-duobu-zhangwang", 53, "原地来回张望，走了好几步", setOf(PetActionTag.WORK)),
        PetAction("haqian-liantian", 54, "冒出对话框开始聊天", setOf(PetActionTag.TALK)),
        PetAction("hudie-mifeng-huanrao-touding-kaihua", 55, "蝴蝶和蜜蜂绕着头顶飞", setOf(PetActionTag.TRICK, PetActionTag.IDLE)),
        PetAction("jin-dai-ding-dang", 56, "系紧的带子叮当作响", setOf(PetActionTag.IDLE)),
        PetAction("jingyu-tu-paopao-texiao", 57, "吐出一串泡泡，很调皮", setOf(PetActionTag.PLAY)),
        PetAction("keai-zhaiwu", 58, "可爱地炸毛", setOf(PetActionTag.MOOD)),
        PetAction("lanjing-xianshi", 59, "冷静地站着不动", setOf(PetActionTag.IDLE)),
        PetAction("lu-mao", 60, "低头让人撸毛，手落在头上", setOf(PetActionTag.PET)),
        PetAction("menghua-xiaoyouling", 61, "梦幻的小幽灵飘过", setOf(PetActionTag.TRICK, PetActionTag.SLEEP)),
        PetAction("nvpu-quxi-liyi", 62, "女仆屈膝行礼，弯腰致意", setOf(PetActionTag.TALK, PetActionTag.PET)),
        PetAction("pangxie-zoulu", 63, "横着挪，像螃蟹一样走", setOf(PetActionTag.IDLE)),
        PetAction("pingkong-shenghua", 64, "手心凭空长出一朵花", setOf(PetActionTag.TRICK)),

        // ---------- 065-080：魔术 + 登场 + 思考 + 节日 ----------
        PetAction("puke-moshu", 65, "变扑克魔术，星星飞出来", setOf(PetActionTag.TRICK)),
        PetAction("qi-muma", 66, "气呼呼地鼓着脸", setOf(PetActionTag.MOOD)),
        PetAction("qian-dai-man-yi", 67, "满脸满意地点头", setOf(PetActionTag.MOOD)),
        PetAction("qian-dai-ru-chang", 68, "举着火炬，郑重登场", setOf(PetActionTag.ENTER)),
        PetAction("qingkuai-jilu", 69, "轻快地记录着什么", setOf(PetActionTag.WORK)),
        PetAction("qingkuai-yaobaiwu", 70, "轻快地摇摆舞动", setOf(PetActionTag.MOOD)),
        PetAction("sanqiu-paojie", 71, "抛接三个球", setOf(PetActionTag.PLAY)),
        PetAction("shendu-sikao-suisuinian", 72, "深度思考，星星在头顶绕", setOf(PetActionTag.WORK, PetActionTag.THINK)),
        PetAction("shia-chishenme", 73, "一脸茫然，脑子空空", setOf(PetActionTag.MOOD)),
        PetAction("shou-hongbao", 74, "接过一个红包", setOf(PetActionTag.GIFT)),
        PetAction("shu-jin-zhou-mei", 75, "捧着书慢慢读", setOf(PetActionTag.IDLE, PetActionTag.WORK)),
        PetAction("shuan-huoguo", 76, "涮火锅，夹起一片肉", setOf(PetActionTag.FESTIVE)),
        PetAction("shuijiao-kaishi", 77, "坐下来准备睡觉", setOf(PetActionTag.SLEEP)),
        PetAction("shuijiao-qilai", 78, "从睡梦中醒来", setOf(PetActionTag.SLEEP)),
        PetAction("shuijiao-xunhuan", 79, "躺平睡觉，循环", setOf(PetActionTag.SLEEP)),
        PetAction("suisuinian-cazhuo-suisuinian", 80, "喃喃自语，碎碎念", setOf(PetActionTag.IDLE, PetActionTag.TALK)),

        // ---------- 081-096：碎碎念 + 体力 + 游戏 + 工作 ----------
        PetAction("suisuinian-duiping-suisuinian", 81, "一边碎碎念一边比划", setOf(PetActionTag.TALK, PetActionTag.IDLE)),
        PetAction("suisuinian-fadai-suisuinian", 82, "说到激动处，比划得更大", setOf(PetActionTag.TALK)),
        PetAction("taotang-nanguadeng", 83, "套上糖画，举着南国灯笼", setOf(PetActionTag.FESTIVE)),
        PetAction("ti-jianzi", 84, "伸懒腰活动身体", setOf(PetActionTag.PET, PetActionTag.IDLE)),
        PetAction("touchi-lingshi-bei-zhuazhu", 85, "偷吃零食被抓住", setOf(PetActionTag.MOOD)),
        PetAction("wan-shuiqiang", 86, "举着水枪打水仗", setOf(PetActionTag.PLAY)),
        PetAction("wan-youxi-qijibaituai", 87, "玩着游戏，耍各种花招", setOf(PetActionTag.PLAY)),
        PetAction("wu-shitou", 88, "捂着头顶缩成一团", setOf(PetActionTag.MOOD)),
        PetAction("xiaofudu-yuandi-360du-xuanzhuan-zhanshi", 89, "原地小幅 360 度旋转展示", setOf(PetActionTag.MOOD, PetActionTag.TRICK)),
        PetAction("xiaotiqin-yanzou", 90, "拉小提琴演奏", setOf(PetActionTag.PLAY, PetActionTag.IDLE)),
        PetAction("xiawuziqi", 91, "下五子棋，专注盯着棋盘", setOf(PetActionTag.WORK, PetActionTag.PLAY)),
        PetAction("xie-daima", 92, "低头写代码", setOf(PetActionTag.WORK)),
        PetAction("xie-fuzi", 93, "挥笔写福字", setOf(PetActionTag.FESTIVE, PetActionTag.WORK)),
        PetAction("yaoshan-naliang", 94, "摇着扇子纳凉", setOf(PetActionTag.IDLE, PetActionTag.FESTIVE)),
        PetAction("yong-jingyu-weiba-paidadi", 95, "用鲸语尾巴拍打地面", setOf(PetActionTag.IDLE, PetActionTag.MOOD)),
        PetAction("youxian-hengga", 96, "悠闲地哼着歌", setOf(PetActionTag.IDLE)),

        // ---------- 097-109：舞 + 小游戏 + 物理 + 换装 ----------
        PetAction("youya-nvpuwu", 97, "优雅地跳女仆舞", setOf(PetActionTag.MOOD, PetActionTag.TRICK)),
        PetAction("yuandi-dunxia-wan-wanju-qiche", 98, "蹲下来玩玩具汽车", setOf(PetActionTag.PLAY)),
        PetAction("yuandi-piaofu-tabu", 99, "原地漂浮踏步", setOf(PetActionTag.IDLE)),
        PetAction("yuandi-qiaoji-zhuomian-hudong", 100, "敲桌子，桌面有互动", setOf(PetActionTag.PLAY, PetActionTag.TALK)),
        PetAction("yuandi-tiaoyue-zhuasui-touding-wupin", 101, "原地起跳，摔碎头顶的物品", setOf(PetActionTag.PLAY, PetActionTag.MOOD)),
        PetAction("yuandi-xiaoqi-chenmian", 102, "小憩一会儿，沉沉睡去", setOf(PetActionTag.SLEEP)),
        PetAction("yuandi-zhongli-xiadun-yasuo", 103, "被重力压得下蹲压缩", setOf(PetActionTag.THROWN, PetActionTag.MOOD)),
        PetAction("yuandi-zhuanxin-wan-mofang", 104, "专心转动手里的魔方", setOf(PetActionTag.PLAY, PetActionTag.WORK)),
        PetAction("yuandi-zuozhuan-benpao", 105, "原地左转，然后奔跑起来", setOf(PetActionTag.PLAY, PetActionTag.ENTER)),
        PetAction("zhao-jingzi", 106, "照镜子整理仪容", setOf(PetActionTag.IDLE, PetActionTag.PET)),
        PetAction("zhengti-huanzhuang-shise", 107, "整体换装着色，从深到浅", setOf(PetActionTag.FESTIVE)),
        PetAction("zhongqiu-shangyue-chi-yuebing", 108, "中秋赏月，吃月饼", setOf(PetActionTag.FESTIVE)),
        PetAction("zhuangdian-shengdanshu", 109, "转圈变成圣诞树", setOf(PetActionTag.FESTIVE, PetActionTag.TRICK)),
    )

    private val BY_ID: Map<String, PetAction> = ALL.associateBy { it.id }

    /** 按 id 查 */
    fun byId(id: String): PetAction? = BY_ID[id]

    /** 按序号查 */
    fun byIndex(index: Int): PetAction? = ALL.firstOrNull { it.index == index }

    /**
     * 按标签取一批动作
     *
     * @param tag 玩法标签
     * @param exclude 要避开的 id（刚播过的那段）
     */
    fun byTag(tag: String, exclude: String? = null): List<PetAction> =
        ALL.filter { tag in it.tags && it.id != exclude }

    /**
     * 随机取一个带指定标签的动作
     *
     * @param tags 候选标签，任一命中即可
     * @param exclude 要避开的 id
     * @param random 随机源
     */
    fun randomByTags(
        tags: Set<String>,
        exclude: String? = null,
        random: kotlin.random.Random = kotlin.random.Random.Default
    ): PetAction? {
        val pool = ALL.filter { action ->
            action.tags.any { it in tags } && action.id != exclude
        }
        return if (pool.isEmpty()) null else pool[random.nextInt(pool.size)]
    }

    /**
     * 资源路径（动画）
     *
     * @param assetBase assets 根路径，默认 "pet/anim/all"
     */
    fun assetPath(action: PetAction, assetBase: String = "pet/anim/all"): String =
        "$assetBase/${action.index.toString().padStart(3, '0')}_${action.id}.webp"

    /**
     * 静态兜底图路径
     */
    fun stillPath(action: PetAction, assetBase: String = "pet/anim/all"): String =
        "$assetBase/${action.index.toString().padStart(3, '0')}_${action.id}_still.webp"
}
