package com.deepfocus.app.ai

import com.deepfocus.app.data.PersonaCardStore
import com.deepfocus.app.data.PersonaProfile
import com.deepfocus.app.data.local.dao.ChatConversationDao
import com.deepfocus.app.data.local.dao.ChatDao
import com.deepfocus.app.data.local.dao.FishStateDao
import com.deepfocus.app.data.local.dao.TravelJournalDao
import com.deepfocus.app.data.local.entity.ChatMessageEntity
import com.deepfocus.app.data.local.entity.FishStateEntity
import com.deepfocus.app.data.repository.FishRepository
import com.deepfocus.app.data.repository.StatsRepositoryImpl
import com.deepfocus.app.util.FocusSessionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 大肥鱼大脑
 *
 * 把「人格卡 + 世界感知 + 对话历史 + 当前状态」组装成一次 AI 调用。
 * 这是大肥鱼所有对外表达的统一出口 —— 无论是用户主动聊天，
 * 还是它自己掷骰触发的主动行为，走的都是这里。
 */
@Singleton
class FishBrain @Inject constructor(
    private val aiConfigStore: AiConfigStore,
    private val personaCardStore: PersonaCardStore,
    private val chatDao: ChatDao,
    private val fishStateDao: FishStateDao,
    private val fishRepository: FishRepository,
    private val statsRepository: StatsRepositoryImpl,
    private val travelJournalDao: TravelJournalDao,
    private val worldContextProvider: WorldContextProvider,
    private val memoryEngine: MemoryEngine,
    private val fishAgent: FishAgent,
    private val conversationManager: ConversationManager,
    private val conversationDao: ChatConversationDao
) {

    companion object {
        /** 上下文窗口包含的最近消息条数 */
    const val CONTEXT_WINDOW = 20

        const val ROLE_USER = "USER"
        const val ROLE_FISH = "FISH"
        const val ROLE_PROACTIVE = "PROACTIVE"
        const val ROLE_SYSTEM = "SYSTEM"

        /** 成块的思考内容（部分推理模型会漏出来） */
        private val thinkingBlockRegex = Regex(
            "<\\s*/?\\s*(think|thinking|thought|reasoning|analysis)\\s*>",
            RegexOption.IGNORE_CASE
        )

        /** 整行的元信息（提示词标题、系统提示之类） */
        private val metaLineRegex = Regex(
            "^[【\\[（(]?\\s*(系统提示|系统消息|系统指令|提示词|指令|思考过程|思维链|推理过程|" +
                "内部提示|人设说明|角色设定|上下文)\\s*[】\\]）)]?\\s*[:：].*$",
            RegexOption.IGNORE_CASE
        )

        /** markdown 粗体标记 */
        private val boldMarkRegex = Regex("\\*\\*")

        /** 角色前缀 */
        private val rolePrefixRegex = Regex(
            "^(assistant|ai|助手)\\s*[:：]\\s*",
            RegexOption.IGNORE_CASE
        )
    }

    /**
     * 当前生效的人格
     *
     * = 当前底稿（内置卡 / 自定义卡）+ 覆盖层。
     *
     * 底稿随好感度自动切（内置包），用户也可主动切到自定义卡；
     * 覆盖层是用户对当前卡的改动，跨档时继续叠在新底稿上 —— 改动被继承。
     */
    suspend fun getPersona(): PersonaProfile {
        val affinity = currentAffinity()
        // 跨档了 → 强制切回内置卡（覆盖层保留）
        personaCardStore.onAffinityChanged(affinity)
        val state = personaCardStore.state()
        val base = personaCardStore.resolveBase(affinity)
        val content = personaCardStore.resolve(base, state)
        return PersonaProfile(
            // 名字的真源是当前激活的大肥鱼，不是人格状态行
            name = fishRepository.getUserFish()?.name ?: "大肥鱼",
            identity = content.identity,
            traits = content.traits,
            speechStyle = content.speechStyle,
            catchphrases = content.catchphrases,
            backstory = content.backstory,
            taboos = content.taboos,
            userAlias = state.userAlias,
            proactiveMotivation = content.proactiveMotivation,
            temperature = content.temperature
        )
    }

    /**
     * 观察当前会话的聊天记录
     *
     * 只看**当前会话** —— 归档掉的旧对话不会混进来，
     * 但它们的内容已经压进交接笔记，会在上下文里出现。
     */
    fun observeMessages(): Flow<List<ChatMessageEntity>> =
        conversationManager.currentId.flatMapLatest { id ->
            chatDao.observeByConversation(id)
        }

    /** 当前大肥鱼的名字（用户起的），没有则用默认名 */
    suspend fun currentFishName(): String =
        fishRepository.getUserFish()?.name ?: "大肥鱼"

    /** 观察状态 */
    fun observeState(): Flow<FishStateEntity?> = fishStateDao.observe()

    /** 获取状态，没有则初始化 */
    suspend fun getState(): FishStateEntity {
        fishStateDao.get()?.let { return it }
        val initial = FishStateEntity()
        fishStateDao.upsert(initial)
        return initial
    }

    /**
     * 发送一条用户消息并流式接收大肥鱼回复
     *
     * @param userInput 用户输入
     * @param onChunk 增量文本回调
     * @param imageBase64 用户随消息发来的图片（base64，不含前缀）；为空表示纯文字
     * @param imageMime 图片 MIME
     * @param imageLocalPath 图片在本地保存的路径（只用于落库展示，不给模型）
     * @return 大肥鱼的完整回复
     */
    suspend fun chat(
        userInput: String,
        onChunk: (String) -> Unit,
        imageBase64: String? = null,
        imageMime: String = "image/jpeg",
        imageLocalPath: String? = null
    ): String {
        // 0. 确定当前会话（没有就建一个）
        val conversationId = conversationManager.ensureCurrent()

        // 1. 记录用户消息（带图时把本地路径一起存下来，气泡里要重画）
        chatDao.insert(
            ChatMessageEntity(
                role = ROLE_USER,
                content = userInput,
                kind = "CHAT",
                imageUri = imageLocalPath,
                conversationId = conversationId
            )
        )
        // 第一句话当会话标题 —— 归档列表里一眼能认出是哪一段
        conversationManager.ensureTitle(conversationId, userInput)
        // 上下文压缩：这段对话太长时，把早期内容压进摘要，
        // 原文不再进上下文窗口 —— 上下文长度因此有界。
        conversationManager.compactIfNeeded(conversationId)

        // 2. 组装上下文
        val persona = getPersona()
        // 温度由人格卡决定 —— 每张卡（内置 6 张 + 自定义）的表达力就该不同。
        // 原代码只发 AiConfig.temperature，人格卡的 temperature 从没进过请求，
        // 结果切档除了文案不同，模型的行为完全一样。
        val config = configWithPersona(aiConfigStore.config.value, persona)
        require(config.isValid) { "尚未配置 AI API" }

        val world = buildWorldContext()
        val mood = FishMoodResolver.resolve(
            affinity = currentAffinity(),
            satiety = world.satiety,
            isHungry = world.isHungry,
            isSleeping = world.isSleeping
        )

        // 记忆注入：已确认的记忆才进 prompt，候选态不参与
        val memories = memoryEngine.selectForInjection(userInput, FocusNarrative.recallQuery(world))
        val memoryContext = memoryEngine.renderForPrompt(memories)

        val systemPrompt = buildString {
            append(PersonaPromptBuilder.build(persona, worldContextProvider.describe(world, persona.userAlias), memoryContext))
            appendLine()
            // 交接笔记：上一段对话压成几百字塞进来 ——
            // 「开新对话但记忆连贯」靠的就是这一段。
            val handoff = conversationManager.handoffNote()
            if (handoff.isNotBlank()) {
                appendLine("【上一段对话（你刚开新对话，这些是你记得的事）】")
                appendLine(handoff)
                appendLine("这是你自己的记忆，接着往下聊就行，别复述它。")
                appendLine()
            }
            // 当前这段对话的压缩摘要 —— 长对话早期内容被压成了它，
            // 原文不再进上下文窗口（避免上下文过载）。
            val ownSummary = conversationManager.currentSummary()
            if (ownSummary.isNotBlank()) {
                appendLine("【这段对话前面聊到哪了】")
                appendLine(ownSummary)
                appendLine()
            }
            appendLine("【你现在的状态】")
            appendLine(mood.description)
            appendLine(mood.actionHint)
            // 表情包清单：模型按语境挑一张，不是关键词猜
            appendLine()
            appendLine("【你可以用的表情包】")
            appendLine(StickerCatalog.catalogForPrompt())
            appendLine("想用表情包时，在回复的最后另起一行写 [表情:表情包id]，")
            appendLine("id 只能从上面清单里选。")
            appendLine("**不是每次都要配图** —— 先想清楚这一句配哪张图最贴切；")
            appendLine("想不出合适的，或者这句话本来就不需要图，就只回文字，别硬凑一张。")
            appendLine("连续几轮都不发表情包很正常。")
            appendLine("历史消息里没有表情标记，只有正文 —— 不要模仿历史，")
            appendLine("要配图就自己按上面的清单选一张。")
        }

        val history = buildHistory()

        // 3. 流式请求（走 agent：她能自己查事实、自己定状态词）
        //
        // 之前是裸 LlmClient.chatStream —— 她只能被动接收 context，
        // 问「我今天用手机多久」只能干瞪眼，状态词也永远轮不到她自己选。
        // 现在换成 fishAgent.runStreaming：工具轮次不吐字，
        // 拿到工具结果后的最终回复才流式吐给 UI。
        val raw = sanitizeReply(
            fishAgent.runStreaming(
                systemPrompt = systemPrompt,
                userPrompt = userInput,
                extraContext = history.joinToString("\n") { "${it.role}: ${it.content}" },
                configOverride = config,
                imageBase64 = imageBase64,
                imageMime = imageMime
            ) { delta ->
                onChunk(sanitizeChunk(delta))
            }.text
        )

        // 4. 拆出表情标记：正文进 content，表情 id 进 action 字段
        val parsed = StickerMarker.parse(raw)
        val reply = parsed.text

        // 5. 落库
        if (reply.isNotBlank()) {
            chatDao.insert(
                ChatMessageEntity(
                    role = ROLE_FISH,
                    content = reply,
                    kind = "CHAT",
                    action = parsed.stickerId,
                    conversationId = conversationId
                )
            )
        }

        // 6. 自动提炼记忆：明确的事实直接记住，含糊的进候选区等人点头
        //    记忆挂上会话 —— 用户删掉这段对话时，这些记忆一起消失。
        memoryEngine.extractCandidates(userInput).forEach { candidate ->
            memoryEngine.remember(candidate.copy(conversationId = conversationId))
        }

        return reply
    }

    /**
     * 触发一次主动行为
     *
     * 与 [chat] 的区别：
     * - 不需要用户输入
     * - 用 proactive prompt，要求它「随手发一条消息」而不是「回答」
     * - 行为名会记录进消息的 action 字段，方便复盘
     *
     * @param action 行为类型
     * @param extraHint 额外上下文（如刚读到的一段书）
     * @param motive 开口的由头，决定注入的动机文案
     * @return 生成的消息，若未触发或失败返回 null
     */
    suspend fun runProactive(
        action: ProactiveAction,
        extraHint: String = "",
        motive: ProactiveMotive? = null
    ): ChatMessageEntity? {
        val persona = getPersona()
        val config = configWithPersona(aiConfigStore.config.value, persona)
        if (!config.isValid || !config.enabled) return null

        val world = buildWorldContext()
        val conversationId = conversationManager.ensureCurrent()

        val mood = FishMoodResolver.resolve(
            affinity = currentAffinity(),
            satiety = world.satiety,
            isHungry = world.isHungry,
            isSleeping = world.isSleeping
        )

        val hint = buildString {
            append(action.hint)
            appendLine()
            // 由头：让模型知道「为什么现在想说话」，避免输出通知腔
            motive?.let {
                appendLine(it.hint)
                appendLine()
            }
            append("【你现在的状态】")
            appendLine(mood.description)
            if (extraHint.isNotBlank()) {
                appendLine(extraHint)
            }
        }

        // 关键：触发文案必须以 user 角色追加，不能用 system/developer，
        // 否则会被端点静默丢弃，模型收不到「你要主动说话」的指令。
        val messages = buildList {
            add("system" to PersonaPromptBuilder.buildProactiveSystem(
                persona,
                worldContextProvider.describe(world, persona.userAlias),
                // 主动开口没有用户输入，就按当前状态捞相关记忆
                memoryEngine.renderForPrompt(memoryEngine.selectForInjection(FocusNarrative.recallQuery(world)))
            ))
            addAll(buildHistory())
            add("user" to PersonaPromptBuilder.buildProactiveTrigger(
                hint,
                mood,
                motivation = persona.proactiveMotivation,
                motive = motive
            ))
        }

        return try {
            // 走 agent（有工具）而不是裸 chatOnce ——
            // 这样她能在同一次交互里自己查事实、自己定状态词。
            //
            // 触发文案是 user 角色（协议要求，见上面注释）。
            val raw = fishAgent.run(
                systemPrompt = buildString {
                    append(
                        PersonaPromptBuilder.buildProactiveSystem(
                            persona,
                            worldContextProvider.describe(world, persona.userAlias),
                            memoryEngine.renderForPrompt(
                                memoryEngine.selectForInjection(FocusNarrative.recallQuery(world))
                            )
                        )
                    )
                    appendLine()
                    // 表情包也交给**她自己**选 ——
                    // 碎碎念同样不该每次都硬配一张图。
                    appendLine("【你可以用的表情包】")
                    appendLine(StickerCatalog.catalogForPrompt())
                    appendLine("想配图时，在最后另起一行写 [表情:表情包id]，id 只能从上面清单里选。")
                    appendLine("**这条消息本来就不需要图的话，就只回一句话** —— 不要为了配图硬凑。")
                },
                userPrompt = PersonaPromptBuilder.buildProactiveTrigger(
                    hint,
                    mood,
                    motivation = persona.proactiveMotivation,
                    motive = motive
                ),
                extraContext = ""
            ).text.trim()

            if (raw.isBlank()) {
                null
            } else {
                // 拆表情标记：她没写就不配图
                val parsed = StickerMarker.parse(raw)
                val entity = ChatMessageEntity(
                    role = ROLE_FISH,
                    content = parsed.text,
                    kind = "PROACTIVE",
                    // action 存表情包 id，不是 ProactiveAction 名 ——
                    // 渲染层靠它决定配哪张图；null = 这次她决定不配图
                    action = parsed.stickerId,
                    conversationId = conversationId
                )
                chatDao.insert(entity)
                // 触发后更新状态
                fishStateDao.markProactive()
                entity
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 发送一条系统事件消息（不请求 AI，仅作为上下文记录）
     *
     * 用于「专注结束」「投喂完成」「被拦截应用」这类事件：
     * 它们不产生气泡，但会在后续对话中成为大肥鱼的话题来源。
     */
    suspend fun pushEvent(content: String, kind: String) {
        chatDao.insert(
            ChatMessageEntity(
                role = ROLE_SYSTEM,
                content = content,
                kind = kind,
                conversationId = conversationManager.ensureCurrent()
            )
        )
    }

    /**
     * 把一次专注记进记忆
     *
     * 转发到 [MemoryEngine.rememberFocusSession]，让大肥鱼记得你们一起熬过的时间。
     */
    suspend fun rememberFocusSession(durationSeconds: Int) {
        memoryEngine.rememberFocusSession(durationSeconds)
    }

    /**
     * 把人格卡的温度覆盖进请求配置
     *
     * 每张人格卡各自带一个温度，
     * 原代码只发 AI 设置里的全局温度，人格卡的温度从没进过请求 ——
     * 结果多档人格除了措辞不同，模型的行为一模一样，切档等于白切。
     *
     * 温度越高越发散：放得开的卡 0.95（放得开）、收着点的 0.9、稳的 0.85。
     *
     * @param config AI 设置里的基础配置
     * @param persona 当前人格卡
     */
    private fun configWithPersona(config: AiConfig, persona: PersonaProfile): AiConfig {
        if (persona.temperature <= 0f) return config
        return config.copy(temperature = persona.temperature)
    }

    /** 清空对话记录 */
    suspend fun clearChat() = chatDao.clearAll()

    /**
     * 开启新对话
     *
     * 旧对话归档（不删），压一张交接笔记给下一段用。
     * 记忆是连贯的 —— 她还记得刚才聊过什么。
     */
    suspend fun startNewConversation(): Long = conversationManager.startNew()

    /** 删除一段对话（连带这段对话里抽出来的记忆一起删） */
    suspend fun deleteConversation(id: Long): Long = conversationManager.delete(id)

    /** 切回某段对话 */
    suspend fun switchConversation(id: Long) = conversationManager.switchTo(id)

    /** 当前会话 id */
    val currentConversationId get() = conversationManager.currentId

    /** 全部会话（用户翻旧对话、删掉不要的） */
    fun observeConversations() = conversationManager.observeConversations()

    /**
     * 构建历史消息列表
     *
     * **只取当前会话的** —— 归档掉的旧对话不进来（它们压成了交接笔记）。
     * SYSTEM 角色的消息会被转成 user 角色的上下文描述，
     * 因为 OpenAI 兼容端点通常不接受连续多条 system 消息。
     */
    private suspend fun buildHistory(): List<LlmMessage> {
        val conversationId = conversationManager.ensureCurrent()
        // 压缩水位之后的消息才进窗口 —— 更早的原文已经被摘要吃掉了，
        // 再喂一遍就是重复 + 上下文过载。
        val watermark = conversationDao.getById(conversationId)?.compactedUpToId ?: 0L
        val recent = chatDao.getRecentByConversation(
            conversationId = conversationId,
            afterId = watermark,
            limit = CONTEXT_WINDOW
        ).reversed()
        return recent.mapNotNull { msg ->
            when (msg.role) {
                ROLE_USER -> LlmMessage("user", msg.content)
                ROLE_FISH -> LlmMessage("assistant", msg.content)
                // 系统事件转成 user 角色的「刚刚发生的事」。
                // 注意用词：写「系统提示」模型会把这四个字原样吐回给用户，
                // 换成生活化的说法，它就当成一件事去接话。
                ROLE_SYSTEM -> LlmMessage("user", "（刚刚发生：${msg.content}）")
                else -> null
            }
        }
    }

    /**
     * 清洗大肥鱼的回复
     *
     * 模型偶尔会把思考过程、提示词标题、角色前缀漏出来。
     * 这些是给用户看的气泡里不该出现的东西，直接抹掉。
     */
    private fun sanitizeReply(raw: String): String {
        if (raw.isBlank()) return raw
        return raw
            .replace(thinkingBlockRegex, "")
            .split('\n')
            .map { it.trim().replace(boldMarkRegex, "").replace(rolePrefixRegex, "") }
            .filterNot { it.isNotEmpty() && metaLineRegex.matches(it) }
            .joinToString("\n")
            .trim()
    }

    /**
     * 流式增量清洗
     *
     * 增量片段不能做 trim 之类的处理 —— 会把跨片段的空格吃掉，
     * 拼出来的句子就黏在一起了。这里只做「整块删除」，不动空白。
     */
    private fun sanitizeChunk(chunk: String): String {
        if (chunk.isEmpty()) return chunk
        val stripped = chunk.replace(thinkingBlockRegex, "")
        if (!stripped.contains('\n')) return stripped
        return stripped.split('\n')
            .filterNot { metaLineRegex.matches(it.trim()) }
            .joinToString("\n")
    }

    /**
     * 采集当前世界快照
     *
     * 除了总数，还要知道「今天分几段」「上一段多久」「刚是不是刚结束」，
     * 大肥鱼才能说出具体的话，而不是甩一个总时长。
     */
    suspend fun buildWorldContext(): WorldContext {
        val state = getState()
        val appUsage = worldContextProvider.collectAppUsage()
        val todayFocus = statsRepository.getTodayFocusDurationMillis()
        val currentFocus = FocusSessionState.accumulatedMillis
        val isFocusing = FocusSessionState.isActive || currentFocus > 0

        val todaySessions = statsRepository.getSessionsByDateRange(startOfDayMillis(), System.currentTimeMillis())
        val completed = todaySessions.filter { it.endTime != null && it.durationSeconds > 0 }
        val lastSession = completed.maxByOrNull { it.endTime ?: 0L }
        val lastFocusMillis = (lastSession?.durationSeconds ?: 0) * 1000L
        val justFinished = !isFocusing &&
            lastSession?.endTime != null &&
            System.currentTimeMillis() - (lastSession.endTime ?: 0L) <= FocusNarrative.JUST_FINISHED_WINDOW_MS

        // 最近一次旅行 —— 她记得自己去过哪、玩得开不开心
        val latestTrip = travelJournalDao.getLatest()

        return WorldContext(
            appUsage = appUsage,
            totalScreenTime = appUsage.sumOf { it.durationMillis },
            todayFocusMillis = todayFocus,
            currentFocusMillis = currentFocus,
            isFocusing = isFocusing,
            // 正在进行的那一段也算一段
            todaySessionCount = completed.size + if (isFocusing) 1 else 0,
            lastFocusMillis = lastFocusMillis,
            justFinishedFocus = justFinished,
            satiety = state.satiety,
            isHungry = FishStateEngine.isHungry(state, System.currentTimeMillis()),
            affinity = currentAffinity(),
            isSleeping = state.isSleeping,
            travelDestination = latestTrip?.destination,
            travelMood = latestTrip?.mood,
            visitedDestinations = travelJournalDao.getVisitedDestinations()
        )
    }

    /** 今天 0 点的时间戳 */
    private fun startOfDayMillis(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** 当前好感度 */
    suspend fun currentAffinity(): Int {
        val fish = fishRepository.getUserFish() ?: return 0
        return fish.affinityLevel
    }
}
