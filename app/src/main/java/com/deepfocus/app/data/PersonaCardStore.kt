package com.deepfocus.app.data

import android.content.Context
import com.deepfocus.app.data.local.dao.PersonaCardDao
import com.deepfocus.app.data.local.dao.PersonaStateDao
import com.deepfocus.app.data.local.entity.PersonaCardEntity
import com.deepfocus.app.data.local.entity.PersonaStateEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 人格卡仓库
 *
 * 管两套卡：
 * - **内置包** —— [BuiltinPersonaCatalog] 提供的静态卡，随好感度自动切换
 * - **自定义包** —— 用户改出来的卡，落库
 *
 * 以及一份**状态行**（[PersonaStateEntity]）：用户自有字段 + 覆盖层 + 底稿选择。
 * 生效人格 = 当前底稿 + 覆盖层。
 *
 * 三条规则（定稿见 docs/adr/0001）：
 * 1. 好感度自动决定当前生效哪一张内置卡，用户不干预切档
 * 2. 跨档时**强制切回**新的内置卡，覆盖层继续叠在新底稿上 —— 改动被继承
 * 3. 用户可主动切到自定义卡；切过去后直到下次跨档被强制切回
 */
@Singleton
class PersonaCardStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cardDao: PersonaCardDao,
    private val stateDao: PersonaStateDao
) {

    /** 观察状态行 */
    fun observeState(): Flow<PersonaStateEntity?> = stateDao.observe()

    /** 观察自定义人格卡包 */
    fun observeCustomCards(): Flow<List<PersonaCardEntity>> = cardDao.observeAll()

    /** 读状态行，没有就建一条空的 */
    suspend fun state(): PersonaStateEntity =
        stateDao.get() ?: PersonaStateEntity().also { stateDao.upsert(it) }

    /** 自定义人格卡包 */
    suspend fun customCards(): List<PersonaCardEntity> = cardDao.getAll()

    /** 按 id 查自定义卡 */
    suspend fun customCard(id: String): PersonaCardEntity? = cardDao.getById(id)

    /** 内置人格卡包（静态） */
    fun builtinCards(): List<BuiltinPersonaCard> = BuiltinPersonaCatalog.cards(context)

    /**
     * 当前底稿
     *
     * 用户显式切到自定义卡 → 用它；
     * 用户显式切到某张**已解锁的内置卡** → 用它；
     * 否则按好感度选内置卡。
     */
    suspend fun resolveBase(affinity: Int): PersonaBase {
        val st = state()
        val customId = st.baseCardId
        if (customId != null && customId.startsWith(CUSTOM_PREFIX)) {
            cardDao.getById(customId)?.let { return PersonaBase.Custom(it) }
        }
        if (customId != null) {
            // 用户手动挑的已解锁内置卡
            BuiltinPersonaCatalog.byId(context, customId)
                ?.takeIf { affinity >= it.affinityThreshold }
                ?.let { return PersonaBase.Builtin(it) }
        }
        BuiltinPersonaCatalog.cardForAffinity(context, affinity)?.let {
            return PersonaBase.Builtin(it)
        }
        // 内置包读不出来（资源缺失）→ 退回自定义包里最新的一张
        return cardDao.getAll().firstOrNull()?.let { PersonaBase.Custom(it) }
            ?: throw IllegalStateException("没有可用的底稿：内置包读不到，自定义包也为空")
    }

    /**
     * 跨档检测：好感度跨过当前档的门槛了没有
     *
     * 跨了 → 切到**新解锁的那张内置卡**，覆盖层保留。
     *
     * 判定依据是「当前档位门槛」变了没有 —— 门槛跟内置卡的 affinityThreshold 一致。
     * 无论当前用的是自定义卡还是用户手动挑的旧内置卡，
     * 只要跨过下一道门槛就自动切到新卡 ——
     * 这就是「好感度解锁新的人格卡」。
     *
     * 但用户手动挑的那张旧卡**会一直有效到下一次跨档** ——
     * 「也可以切换回之前解锁过的」（2026-11-18 用户要求）。
     *
     * @return true 表示发生了跨档切卡
     */
    suspend fun onAffinityChanged(affinity: Int): Boolean {
        val st = state()
        val tier = BuiltinPersonaCatalog.cardForAffinity(context, affinity)?.affinityThreshold ?: 0
        if (tier == st.selectedTierThreshold) return false

        // 跨档了 → 切到新解锁的内置卡
        stateDao.upsert(
            st.copy(
                baseCardId = null,
                selectedTierThreshold = tier,
                updatedAt = System.currentTimeMillis()
            )
        )
        return true
    }

    /**
     * 保存用户对当前生效卡的改动 → 写进覆盖层
     *
     * 只写有改动的字段；改回底稿原值等于没改，直接记 null（不覆盖底稿）。
     *
     * @param base 当前底稿的值，用来判断「到底改了什么」
     */
    suspend fun saveOverlay(
        base: PersonaBase,
        identity: String,
        traits: String,
        speechStyle: String,
        catchphrases: String,
        backstory: String,
        taboos: String,
        proactiveMotivation: String,
        temperature: Float
    ) {
        val st = state()
        stateDao.upsert(
            st.copy(
                identity = identity.takeIf { it != base.identity },
                traits = traits.takeIf { it != base.traits },
                speechStyle = speechStyle.takeIf { it != base.speechStyle },
                catchphrases = catchphrases.takeIf { it != base.catchphrases },
                backstory = backstory.takeIf { it != base.backstory },
                taboos = taboos.takeIf { it != base.taboos },
                proactiveMotivation = proactiveMotivation.takeIf { it != base.proactiveMotivation },
                temperature = temperature.takeIf { it != base.temperature },
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    /**
     * 另存为自定义人格卡
     *
     * 把**当前生效内容**（底稿 + 覆盖层）快照进自定义包。
     * 切过去后它就是新的底稿，与内置包互不影响。
     *
     * @return 新卡的 id
     */
    suspend fun saveAsCustomCard(
        base: PersonaBase,
        state: PersonaStateEntity,
        displayName: String,
        tagline: String = ""
    ): String {
        val resolved = resolve(base, state)
        val id = CUSTOM_PREFIX + System.currentTimeMillis()
        cardDao.upsert(
            PersonaCardEntity(
                id = id,
                displayName = displayName,
                tagline = tagline,
                identity = resolved.identity,
                traits = resolved.traits,
                speechStyle = resolved.speechStyle,
                catchphrases = resolved.catchphrases,
                backstory = resolved.backstory,
                taboos = resolved.taboos,
                proactiveMotivation = resolved.proactiveMotivation,
                temperature = resolved.temperature
            )
        )
        return id
    }

    /** 切到某张自定义卡 */
    suspend fun selectCustomCard(id: String, affinity: Int) {
        val st = state()
        val tier = BuiltinPersonaCatalog.cardForAffinity(context, affinity)?.affinityThreshold ?: 0
        stateDao.upsert(
            st.copy(
                baseCardId = id,
                selectedTierThreshold = tier,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    /** 切回内置卡（跟随好感度） */
    suspend fun selectBuiltin(affinity: Int) {
        val st = state()
        val tier = BuiltinPersonaCatalog.cardForAffinity(context, affinity)?.affinityThreshold ?: 0
        stateDao.upsert(
            st.copy(
                baseCardId = null,
                selectedTierThreshold = tier,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    /**
     * 切到某张**已解锁的内置卡**
     *
     * 2026-11-18 用户要求：「好感度是解锁新的人格卡，
     * 但是也可以切换回之前解锁过的」。
     *
     * 没解锁的卡不让切（门槛没到就是没到，不能提前用）。
     * 切过去后一直有效到下一次跨档 —— 那时自动切到新解锁的卡。
     *
     * @return true 表示切成功
     */
    suspend fun selectBuiltinCard(id: String, affinity: Int): Boolean {
        val card = BuiltinPersonaCatalog.byId(context, id) ?: return false
        if (affinity < card.affinityThreshold) return false
        val st = state()
        val tier = BuiltinPersonaCatalog.cardForAffinity(context, affinity)?.affinityThreshold ?: 0
        stateDao.upsert(
            st.copy(
                baseCardId = id,
                selectedTierThreshold = tier,
                updatedAt = System.currentTimeMillis()
            )
        )
        return true
    }

    /** 删除自定义卡；若删的是当前底稿，回落到内置卡 */
    suspend fun deleteCustomCard(id: String, affinity: Int) {
        cardDao.deleteById(id)
        val st = state()
        if (st.baseCardId == id) {
            selectBuiltin(affinity)
        }
    }

    /** 清空覆盖层（恢复底稿原样） */
    suspend fun clearOverlay() {
        val st = state()
        stateDao.upsert(
            st.copy(
                identity = null,
                traits = null,
                speechStyle = null,
                catchphrases = null,
                backstory = null,
                taboos = null,
                proactiveMotivation = null,
                temperature = null,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    /** 保存它对用户的称呼 */
    suspend fun saveUserAlias(userAlias: String) {
        val st = state()
        stateDao.upsert(
            st.copy(
                userAlias = userAlias,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    /** 当前底稿的 id（自定义卡 id 或内置卡 id） */
    suspend fun baseCardId(affinity: Int): String = when (val base = resolveBase(affinity)) {
        is PersonaBase.Custom -> base.card.id
        is PersonaBase.Builtin -> base.card.id
    }

    /** 底稿的展示名 */
    fun displayNameOf(base: PersonaBase): String = when (base) {
        is PersonaBase.Custom -> base.card.displayName
        is PersonaBase.Builtin -> base.card.displayName
    }

    /**
     * 底稿 + 覆盖层 = 生效人格的内容
     *
     * 覆盖层里非 null 的字段盖住底稿。
     */
    fun resolve(base: PersonaBase, state: PersonaStateEntity): PersonaContent =
        PersonaContent(
            identity = state.identity ?: base.identity,
            traits = state.traits ?: base.traits,
            speechStyle = state.speechStyle ?: base.speechStyle,
            catchphrases = state.catchphrases ?: base.catchphrases,
            backstory = state.backstory ?: base.backstory,
            taboos = state.taboos ?: base.taboos,
            proactiveMotivation = state.proactiveMotivation ?: base.proactiveMotivation,
            temperature = state.temperature ?: base.temperature
        )

    companion object {
        /** 自定义卡 id 前缀 */
        const val CUSTOM_PREFIX = "custom_"
    }
}

/**
 * 当前底稿：要么是内置卡，要么是自定义卡
 */
sealed class PersonaBase {
    abstract val identity: String
    abstract val traits: String
    abstract val speechStyle: String
    abstract val catchphrases: String
    abstract val backstory: String
    abstract val taboos: String
    abstract val proactiveMotivation: String
    abstract val temperature: Float

    data class Builtin(val card: BuiltinPersonaCard) : PersonaBase() {
        override val identity get() = card.identity
        override val traits get() = card.traits
        override val speechStyle get() = card.speechStyle
        override val catchphrases get() = card.catchphrases
        override val backstory get() = card.backstory
        override val taboos get() = card.taboos
        override val proactiveMotivation get() = card.proactiveMotivation
        override val temperature get() = card.temperature
    }

    data class Custom(val card: PersonaCardEntity) : PersonaBase() {
        override val identity get() = card.identity
        override val traits get() = card.traits
        override val speechStyle get() = card.speechStyle
        override val catchphrases get() = card.catchphrases
        override val backstory get() = card.backstory
        override val taboos get() = card.taboos
        override val proactiveMotivation get() = card.proactiveMotivation
        override val temperature get() = card.temperature
    }
}

/**
 * 生效人格的内容（底稿 + 覆盖层的结果）
 */
data class PersonaContent(
    val identity: String,
    val traits: String,
    val speechStyle: String,
    val catchphrases: String,
    val backstory: String,
    val taboos: String,
    val proactiveMotivation: String,
    val temperature: Float
)
