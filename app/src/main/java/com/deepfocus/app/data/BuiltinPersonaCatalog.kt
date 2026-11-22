package com.deepfocus.app.data

import android.content.Context
import org.json.JSONObject

/**
 * 内置人格卡目录
 *
 * 正文来自 dsh 生态高星开源仓库里**别人写好的**人设，不自己重写。
 * 静态资源放在 `assets/personas/builtin_cards.json`，App 启动时读一次。
 *
 * 许可与来源见 `artwork/persona_sources/MANIFEST.md`：
 * 选中的 6 个仓库均为 MIT，二创须保留署名。
 *
 * 卡与好感度里程碑一一对齐 —— 好感度到了，卡就换。
 */
object BuiltinPersonaCatalog {

    /** 资源路径 */
    private const val ASSET_PATH = "personas/builtin_cards.json"

    /** 缓存：读一次就够，进程内不变 */
    @Volatile
    private var cached: List<BuiltinPersonaCard>? = null

    /**
     * 全部内置卡，按好感度门槛升序
     *
     * 读失败时返回空列表 —— 上层会退回「用户自填的默认人格」，
     * 不让 App 因为一个资源文件挂掉。
     */
    fun cards(context: Context): List<BuiltinPersonaCard> {
        cached?.let { return it }
        val parsed = synchronized(this) {
            cached ?: load(context).also { cached = it }
        }
        return parsed
    }

    private fun load(context: Context): List<BuiltinPersonaCard> {
        return try {
            val text = context.assets.open(ASSET_PATH).use { it.readBytes().toString(Charsets.UTF_8) }
            val root = JSONObject(text)
            val arr = root.getJSONArray("cards")
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.getJSONObject(i)
                BuiltinPersonaCard(
                    id = obj.getString("id"),
                    displayName = obj.getString("displayName"),
                    tagline = obj.optString("tagline"),
                    affinityThreshold = obj.getInt("affinityThreshold"),
                    sourceRepo = obj.optString("sourceRepo"),
                    sourceLicense = obj.optString("sourceLicense"),
                    identity = obj.getString("identity"),
                    traits = obj.getString("traits"),
                    speechStyle = obj.getString("speechStyle"),
                    catchphrases = obj.getString("catchphrases"),
                    backstory = obj.getString("backstory"),
                    taboos = obj.getString("taboos"),
                    proactiveMotivation = obj.getString("proactiveMotivation"),
                    temperature = obj.getDouble("temperature").toFloat()
                )
            }.sortedBy { it.affinityThreshold }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 按好感度选出当前该用的内置卡
     *
     * 门槛是「好感度 ≥ 阈值」，取满足的最大阈值那张。
     * 好感度为 0 时返回门槛最低的那张（一定是 affinityThreshold = 0 的第一张）。
     */
    fun cardForAffinity(context: Context, affinity: Int): BuiltinPersonaCard? {
        val all = cards(context)
        if (all.isEmpty()) return null
        return all.lastOrNull { affinity >= it.affinityThreshold } ?: all.first()
    }

    /** 按 id 查内置卡 */
    fun byId(context: Context, id: String): BuiltinPersonaCard? =
        cards(context).firstOrNull { it.id == id }
}

/**
 * 一张内置人格卡
 *
 * 字段与 `PersonaEntity` 一一对应 —— 应用到人格实体时整套覆盖。
 */
data class BuiltinPersonaCard(
    val id: String,
    val displayName: String,
    val tagline: String,
    /** 生效所需的好感度门槛 */
    val affinityThreshold: Int,
    /** 来源仓库，用于署名 */
    val sourceRepo: String,
    /** 许可标识 */
    val sourceLicense: String,
    val identity: String,
    val traits: String,
    val speechStyle: String,
    val catchphrases: String,
    val backstory: String,
    val taboos: String,
    val proactiveMotivation: String,
    val temperature: Float
)
