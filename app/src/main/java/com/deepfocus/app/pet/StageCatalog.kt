package com.deepfocus.app.pet

/**
 * 舞台动作白名单 + 进 APK 的动作全集。
 *
 * 由 tools/media/gen_stage_catalog.py 生成，请勿手改。
 *
 * ## 两个集合的区别（别混）
 *
 * | 集合 | 含义 | 谁在用 |
 * |---|---|---|
 * | `STAGE_ACTIONS` | **能上台** —— 角色高度与其他动作一致，横向不摊太开 | 首页轮播 / 喂食发牌堆 |
 * | `SHIPPED_ACTIONS` | **进 APK** —— 素材文件存在 | 桌宠按标签抽动作 |
 *
 * ## 为什么有第二个集合（2026-11-19）
 *
 * 用户要求：「隔离的不需要放进apk了，利用效率不高」→
 * 隔离动作（归一化宽度 > 768px）的素材已从 APK 删除，省 ~69 MB。
 *
 * 但桌宠是**独立小窗**，尺寸小，宽一点没关系 ——
 * 它按**标签**从动作库里抽（`PetPlayEngine.pick`），
 * 不走舞台白名单。所以有几个玩法（摸头 / 送礼 / 思考）
 * 在「能上台」集合里候选为 0，删干净了玩法就废了。
 *
 * 这几个标签各保留几个隔离动作：
 *
 * | 标签 | 玩法 | 保留 |
 * |---|---|---|
| | `PET` | PET 类 | `lu-mao`, `chaoda-shenlanyao` |
| | `GIFT` | GIFT 类 | `chai-liwu`, `shou-hongbao` |
| | `THINK` | THINK 类 | `gongzuozhuangtai-sikao-maopao`, `shendu-sikao-suisuinian` |
 *
 * **改动作标签后一定要重跑本脚本**，否则新标签的动作进不了轮播、
 * 新的隔离动作也来不及从 APK 里剔掉。
 */
object StageCatalog {

    /** 可上台的动作 id（按出现顺序） */
    val STAGE_ACTIONS: Set<String> = setOf(
        "beixiayitiao-zhamao",
        "chi-baifan",
        "chi-bingqilin-ronghua",
        "chi-changshoumian",
        "chi-chongyanggao",
        "chi-dazhaxie",
        "chi-labazhou",
        "chi-niangao",
        "chi-qingtuan",
        "chi-tanghulu",
        "chi-token",
        "chi-wancan",
        "chi-wucan",
        "chi-xigua",
        "chi-zaocan",
        "chi-zongzi",
        "chijiaozi",
        "chitangyuan",
        "chou-tuoluo",
        "chuanzhenqiqiao",
        "da-keshui-bei-jingxing",
        "dangqiuqian",
        "dianji-huiying-aojiao-shengqi-ceshen-zhanshi",
        "dianji-huiying-haixiu-jingya",
        "dianji-huiying-kaixin-yuedong",
        "dianji-huiying-naoyang-gegexiao",
        "dianji-huiying-yuanqi-huishou",
        "dongwu-huanrao",
        "duixueren",
        "fang-fengzheng",
        "fang-kongmingdeng",
        "fanghedeng",
        "fen-wen-bu-sheng",
        "gongzuozhuangtai-chuitou-tanqi-maohan",
        "gongzuozhuangtai-queyue-qingzhu",
        "jingyu-tu-paopao-texiao",
        "keai-zhaiwu",
        "menghua-xiaoyouling",
        "pingkong-shenghua",
        "puke-moshu",
        "qi-muma",
        "qian-dai-man-yi",
        "qian-dai-ru-chang",
        "qingkuai-yaobaiwu",
        "sanqiu-paojie",
        "shuan-huoguo",
        "taotang-nanguadeng",
        "touchi-lingshi-bei-zhuazhu",
        "wan-youxi-qijibaituai",
        "wu-shitou",
        "xiaofudu-yuandi-360du-xuanzhuan-zhanshi",
        "xiaotiqin-yanzou",
        "xiawuziqi",
        "xie-fuzi",
        "yaoshan-naliang",
        "yong-jingyu-weiba-paidadi",
        "youya-nvpuwu",
        "yuandi-dunxia-wan-wanju-qiche",
        "yuandi-qiaoji-zhuomian-hudong",
        "yuandi-tiaoyue-zhuasui-touding-wupin",
        "yuandi-zhongli-xiadun-yasuo",
        "yuandi-zhuanxin-wan-mofang",
        "yuandi-zuozhuan-benpao",
        "zhengti-huanzhuang-shise",
        "zhongqiu-shangyue-chi-yuebing",
        "zhuangdian-shengdanshu",
    )

    /**
     * 进 APK 的动作 id —— 素材文件确实存在的那些。
     *
     * 桌宠按标签抽动作时必须用这个集合，
     * 抽到集合外的动作 → 素材不存在 → 动画静默播不出来。
     */
    val SHIPPED_ACTIONS: Set<String> = setOf(
        "beixiayitiao-zhamao",
        "chai-liwu",
        "chaoda-shenlanyao",
        "chi-baifan",
        "chi-bingqilin-ronghua",
        "chi-changshoumian",
        "chi-chongyanggao",
        "chi-dazhaxie",
        "chi-labazhou",
        "chi-niangao",
        "chi-qingtuan",
        "chi-tanghulu",
        "chi-token",
        "chi-wancan",
        "chi-wucan",
        "chi-xigua",
        "chi-zaocan",
        "chi-zongzi",
        "chijiaozi",
        "chitangyuan",
        "chou-tuoluo",
        "chuanzhenqiqiao",
        "da-keshui-bei-jingxing",
        "dangqiuqian",
        "dianji-huiying-aojiao-shengqi-ceshen-zhanshi",
        "dianji-huiying-haixiu-jingya",
        "dianji-huiying-kaixin-yuedong",
        "dianji-huiying-naoyang-gegexiao",
        "dianji-huiying-yuanqi-huishou",
        "dongwu-huanrao",
        "duixueren",
        "fang-fengzheng",
        "fang-kongmingdeng",
        "fanghedeng",
        "fen-wen-bu-sheng",
        "gongzuozhuangtai-chuitou-tanqi-maohan",
        "gongzuozhuangtai-queyue-qingzhu",
        "gongzuozhuangtai-sikao-maopao",
        "jingyu-tu-paopao-texiao",
        "keai-zhaiwu",
        "lu-mao",
        "menghua-xiaoyouling",
        "pingkong-shenghua",
        "puke-moshu",
        "qi-muma",
        "qian-dai-man-yi",
        "qian-dai-ru-chang",
        "qingkuai-yaobaiwu",
        "sanqiu-paojie",
        "shendu-sikao-suisuinian",
        "shou-hongbao",
        "shuan-huoguo",
        "taotang-nanguadeng",
        "touchi-lingshi-bei-zhuazhu",
        "wan-youxi-qijibaituai",
        "wu-shitou",
        "xiaofudu-yuandi-360du-xuanzhuan-zhanshi",
        "xiaotiqin-yanzou",
        "xiawuziqi",
        "xie-fuzi",
        "yaoshan-naliang",
        "yong-jingyu-weiba-paidadi",
        "youya-nvpuwu",
        "yuandi-dunxia-wan-wanju-qiche",
        "yuandi-qiaoji-zhuomian-hudong",
        "yuandi-tiaoyue-zhuasui-touding-wupin",
        "yuandi-zhongli-xiadun-yasuo",
        "yuandi-zhuanxin-wan-mofang",
        "yuandi-zuozhuan-benpao",
        "zhengti-huanzhuang-shise",
        "zhongqiu-shangyue-chi-yuebing",
        "zhuangdian-shengdanshu",
    )

}
