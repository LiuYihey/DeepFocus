package com.deepfocus.app.ai

import com.deepfocus.app.data.local.dao.FishMemoryDao
import com.deepfocus.app.data.local.entity.FishMemoryEntity
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow

/**
 * 大肥鱼的记忆引擎
 *
 * 三个改造点：
 * 1. **提炼到句子级**：不再把整条消息塞进记忆，
 *    而是把「我今天要考试但我好慌」拆成「我今天要考试」这一句。
 * 2. **时效分层**：今天 / 最近几天 / 久远，
 *    久远的记忆权重压得很低，而且必须和当前话题真的相关才捞得出来。
 * 3. **自动记住**：明确的���实（"我叫X"、"我在找工作"、"我不喜欢早起"）
 *    直接进记忆；含糊的才进候选区等人点头。用户不需要手动填任何东西。
 *
 * 借鉴 dsh-fish-memory 的实测参数（省 token 又保"她记得"）：
 * - 普通记忆半衰期 30 天，每轮最多注入 6 条 / 800 字
 * - 长期事实每轮固定带 2 条
 *
 * 借鉴 dsh-meow-memory 的分层：关系记忆（关于用户）与任务记忆（做过的事）
 * 分开存、分开衰减 —— 压缩上下文后不会出现"新魂旧壳"。
 */
@Singleton
class MemoryEngine @Inject constructor(
    private val memoryDao: FishMemoryDao
) {

    companion object {
        /** 每轮最多注入的记忆条数 */
        const val MAX_INJECT = 6

        /** 每轮最多注入的记忆总字数 */
        const val MAX_CHARS = 800

        /** 每轮固定注入的长期事实条数 */
        const val PINNED_COUNT = 2

        /** 低于此权重的记忆不再注入（衰减到底了） */
        const val MIN_WEIGHT = 0.05f

        /** 一天毫秒数 */
        private const val DAY_MS = 24 * 60 * 60 * 1000.0

        /** 最近几天（7 天内） */
        private const val RECENT = 7.0

        /** 更久一点 */
        private const val OLDER = 30.0

        /** 长期不变的事实（关于用户本人） */
        private val RELATION_PATTERNS = listOf(
            "我叫", "我的名字", "叫我", "我住在", "我是", "我工作在", "我学",
            "我喜欢", "我最爱", "我讨厌", "我不喜欢", "我不爱", "我受不了",
            "我怕", "我不怕", "我不吃", "我不能吃", "我过敏", "我忌口",
            "我每天", "我通常", "我习惯", "我一般", "我从来不", "我总是",
            "我生日", "我在准备", "我在找工作", "我在面试", "我在实习",
            "我在读", "我今年", "我刚离职", "我裸辞"
        )

        /** 只在当天有效的事（今天要考试、明天要交） */
        private val TODAY_PATTERNS = listOf(
            "今天要", "今天得", "今天去", "今天有", "今晚", "今早",
            "明天要", "明天得", "明天去", "明天有", "明晚",
            "后天要", "下午要", "上午要", "中午要",
            "考试", "面试", "答辩", "作业", "论文", "汇报", "答辩课",
            "截止", "deadline", "入职", "出差", "体检", "搬家", "报名",
            "开庭", "答辩", "交稿", "验收"
        )

        /** 做过的事、还在推进的事 */
        private val TASK_PATTERNS = listOf(
            "昨天", "前天", "上周", "这周", "下周", "上个月", "这个月",
            "待办", "要做", "得做", "得去", "计划", "准备", "安排"
        )

        /** 强事实：说出口就基本不会反悔，可以直接记住 */
        private val STRONG_FACT_PATTERNS = listOf(
            "我叫", "我的名字", "叫我", "我喜欢", "我最爱", "我讨厌", "我不喜欢",
            "我怕", "我不吃", "我过敏", "我每天", "我习惯", "我住在",
            "我在找工作", "我在面试", "我在实习", "我刚离职", "我裸辞", "我今年"
        )

        /** 句子分隔符：把一段话拆成一个个「一件事」 */
        private val CLAUSE_SPLITTERS = charArrayOf(
            '，', ',', '。', '！', '!', '？', '?', '；', ';', '、', '\n'
        )

        /** 多字分隔符：先替换成单字再切 */
        private val CLAUSE_PHRASES = listOf("但是", "不过", "可是", "然后", "而且")
    }

    /**
     * 抽取值得记的事
     *
     * 纯本地规则，不调模型 —— 每次对话多一次模型调用不值当，
     * 而且规则抽出来的东西更可控。
     *
     * 返回值带 status：
     * - 强事实 / 当天的事 → CONFIRMED，直接记住
     * - 其余 → CANDIDATE，等用户在记忆页点头
     *
     * @param userInput 用户刚说的那句话
     */
    fun extractCandidates(userInput: String): List<FishMemoryEntity> {
        val clauses = splitClauses(userInput)
        if (clauses.isEmpty()) return emptyList()

        val result = mutableListOf<FishMemoryEntity>()
        for (clause in clauses) {
            val content = clause.trim()
            // 太短没信息量，太长不像"一件事"
            if (content.length < 4 || content.length > 40) continue

            val kind = classify(content) ?: continue
            result += FishMemoryEntity(
                kind = kind,
                content = content,
                status = statusFor(kind, content)
            )
            if (result.size >= 2) break
        }
        return result
    }

    /**
     * 存一条记忆
     *
     * 按记忆自身的状态存 —— 强事实直接进记忆，含糊的进候选区。
     * 重复的（同一件事说了两遍）不重复存。
     */
    suspend fun remember(memory: FishMemoryEntity): Boolean {
        val content = memory.content.trim()
        if (content.isEmpty()) return false
        if (isDuplicate(content)) return false
        memoryDao.insert(memory.copy(content = content))
        return true
    }

    /** 存一条候选记忆（等人点头） */
    suspend fun rememberCandidate(content: String, kind: String) {
        remember(
            FishMemoryEntity(
                kind = kind,
                content = content,
                status = FishMemoryEntity.STATUS_CANDIDATE
            )
        )
    }

    /**
     * 记住一次专注
     *
     * 设计初衷：「AI 能知道今天专注了多久」不只是回答当下的问题，
     * 还要让大肥鱼**记得**你们一起熬过的时间 —— 否则它只是查了个数据库，
     * 不是真的跟你一起扛过。
     *
     * 记成 TASK（做过的事），不是 RELATION（关于用户本人的事）。
     * 直接确认，不用等人点头 —— 专注时长是客观事实，不是主观猜测。
     */
    suspend fun rememberFocusSession(durationSeconds: Int) {
        val minutes = durationSeconds / 60
        if (minutes < 1) return
        val content = if (minutes >= 60) {
            "我陪你专注了 ${minutes / 60} 小时 ${minutes % 60} 分钟"
        } else {
            "我陪你专注了 $minutes 分钟"
        }
        remember(
            FishMemoryEntity(
                kind = FishMemoryEntity.KIND_TASK,
                content = content,
                status = FishMemoryEntity.STATUS_CONFIRMED
            )
        )
    }

    /** 用户确认了一条记忆 */
    suspend fun confirm(id: Long) = memoryDao.setStatus(id, FishMemoryEntity.STATUS_CONFIRMED)

    /** 用户拒绝了一条记忆 */
    suspend fun dismiss(id: Long) = memoryDao.deleteById(id)

    /** 置顶/取消置顶（长期事实） */
    suspend fun togglePinned(memory: FishMemoryEntity) {
        memoryDao.update(memory.copy(isPinned = !memory.isPinned))
    }

    /** 观察全部记忆 */
    fun observeAll() = memoryDao.observeAll()

    /** 观察候选记忆 */
    fun observeCandidates() = memoryDao.observeByStatus(FishMemoryEntity.STATUS_CANDIDATE)

    /** 清空记忆 */
    suspend fun clearAll() = memoryDao.clearAll()

    /**
     * 选出本轮要注入的记忆
     *
     * 权重 = 时效 × 半衰期衰减 × 复述次数加成 × 关键词相关度。
     *
     * 召回原则：
     * - 当天的事权重最高（她应该记得今天在干嘛）
     * - 最近几天的正常
     * - 久远的必须和当前话题真的相关才捞得出来，否则不提
     * - 只捞相关的，不全量塞
     *
     * @param context 当前话题（用户刚说了什么）
     * @param stateHint 当前状态关键词（今天专注了多久、段数等）
     */
    suspend fun selectForInjection(context: String, stateHint: String = ""): List<FishMemoryEntity> {
        val confirmed = memoryDao.getByStatus(FishMemoryEntity.STATUS_CONFIRMED)
        if (confirmed.isEmpty()) return emptyList()

        val now = System.currentTimeMillis()
        val query = listOf(context, stateHint).filter { it.isNotBlank() }.joinToString(" ")

        val scored = confirmed.mapNotNull { memory ->
            // 当天的事过期了就不该再提（昨天的考试今天别问）
            if (memory.kind == FishMemoryEntity.KIND_TODAY && !isSameDay(memory.createdAt, now)) {
                return@mapNotNull null
            }
            val relevance = relevance(memory.content, query)
            // 久远的记忆：必须真的相关才捞，否则不提
            if (ageDays(memory, now) > OLDER && relevance < 0.9f) {
                return@mapNotNull null
            }
            // 超过「最近几天」的记忆也不该无条件灌进 prompt ——
            // 否则大肥鱼会翻出一周前的旧事主动翻旧账，用户觉得它在监视。
            // 相关度得够高（≥1.0，即至少两个词命中）才值得提。
            if (ageDays(memory, now) > RECENT && relevance < 1.0f) {
                return@mapNotNull null
            }
            memory to (score(memory, relevance, now) to relevance)
        }.filter { it.second.first >= MIN_WEIGHT }
            .sortedByDescending { it.second.first }

        // 长期事实固定带 2 条（不受排序影响）
        val pinned = confirmed.filter { it.isPinned }.take(PINNED_COUNT)
        val pinnedIds = pinned.map { it.id }.toSet()

        val picked = LinkedHashSet<FishMemoryEntity>()
        var chars = 0
        for (memory in scored) {
            if (picked.size >= MAX_INJECT) break
            if (memory.first.id in pinnedIds) continue
            val len = memory.first.content.length
            if (chars + len > MAX_CHARS && picked.isNotEmpty()) continue
            picked.add(memory.first)
            chars += len
        }
        pinned.forEach { picked.add(it) }

        val result = picked.toList()
        if (result.isNotEmpty()) {
            memoryDao.markRecalled(result.map { it.id }, now)
        }
        return result
    }

    /**
     * 把选中的记忆渲染成 prompt 片段
     *
     * 按时效分三档写进 prompt，模型才知道哪件该现在提、哪件别主动提。
     */
    fun renderForPrompt(memories: List<FishMemoryEntity>): String {
        if (memories.isEmpty()) return ""
        val now = System.currentTimeMillis()
        val today = memories.filter { isSameDay(it.createdAt, now) }
        val recent = memories.filter { !isSameDay(it.createdAt, now) && ageDays(it, now) <= RECENT }
        val old = memories.filter { ageDays(it, now) > RECENT }

        return buildString {
            appendLine("【你记得的事】")
            if (today.isNotEmpty()) {
                appendLine("今天的事：")
                today.forEach { appendLine("- ${it.content}") }
            }
            if (recent.isNotEmpty()) {
                appendLine("前阵子的事：")
                recent.forEach { appendLine("- ${it.content}") }
            }
            if (old.isNotEmpty()) {
                appendLine("很久以前的事：")
                old.forEach { appendLine("- ${it.content}") }
            }
            appendLine("这些是你真的记得的事。要接话的时候顺手带一句，一次最多提一件，别念档案。")
            appendLine("今天的事只在今天说；很久以前的事除非他先开口，否则别主动提。")
            appendLine("记不清就说「诶我好像忘了…你再说一遍？」——不要编。")
        }
    }

    // ===== 内部实现 =====

    /** 把一段话拆成一个个「一件事」 */
    private fun splitClauses(text: String): List<String> {
        val normalized = CLAUSE_PHRASES.fold(text) { acc, phrase -> acc.replace(phrase, "，") }
        val buffer = StringBuilder()
        val result = mutableListOf<String>()
        for (ch in normalized) {
            if (CLAUSE_SPLITTERS.contains(ch)) {
                if (buffer.isNotBlank()) result += buffer.toString()
                buffer.clear()
            } else {
                buffer.append(ch)
            }
        }
        if (buffer.isNotBlank()) result += buffer.toString()
        return result.map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** 判断这句话属于哪一类记忆，认不出来就 null */
    private fun classify(text: String): String? = when {
        TODAY_PATTERNS.any { text.contains(it) } -> FishMemoryEntity.KIND_TODAY
        RELATION_PATTERNS.any { text.contains(it) } -> FishMemoryEntity.KIND_RELATION
        TASK_PATTERNS.any { text.contains(it) } -> FishMemoryEntity.KIND_TASK
        else -> null
    }

    /**
     * 决定这条记忆直接记住，还是先存候选
     *
     * 强事实（"我叫X"、"我在找工作"）直接记住 —— 那是对方亲口说的，
     * 记住不算是撒谎。含糊的（"我最近挺烦的"）先存候选，等人点头。
     */
    private fun statusFor(kind: String, text: String): String = when {
        kind == FishMemoryEntity.KIND_TODAY -> FishMemoryEntity.STATUS_CONFIRMED
        STRONG_FACT_PATTERNS.any { text.contains(it) } -> FishMemoryEntity.STATUS_CONFIRMED
        else -> FishMemoryEntity.STATUS_CANDIDATE
    }

    /** 查重：同一件事不记两遍 */
    private suspend fun isDuplicate(content: String): Boolean {
        val normalized = normalize(content)
        if (normalized.length < 4) return false
        return memoryDao.getAll().any { existing ->
            val other = normalize(existing.content)
            other == normalized || (other.contains(normalized) && normalized.length >= 6)
        }
    }

    /** 归一化：去掉标点和空白，只比语义主体 */
    private fun normalize(text: String): String =
        text.filter { it.isLetterOrDigit() }

    /**
     * 记忆权重
     *
     * @param relevance 关键词相关度（0~1.5）
     */
    private fun score(memory: FishMemoryEntity, relevance: Float, now: Long): Float {
        // 置顶：直接给满分权重
        if (memory.isPinned) return 1.0f

        val halfLifeDays = when (memory.kind) {
            FishMemoryEntity.KIND_RELATION -> FishMemoryEntity.HALF_LIFE_DAYS_RELATION
            FishMemoryEntity.KIND_TODAY -> FishMemoryEntity.HALF_LIFE_DAYS_TODAY
            else -> FishMemoryEntity.HALF_LIFE_DAYS_TASK
        }
        val daysSinceRecall = (now - memory.lastRecalledAt) / DAY_MS
        val decay = 0.5.pow(daysSinceRecall / halfLifeDays)

        // 复述次数加成：被提起过多次的记忆更牢固（封顶 2 倍）
        val recallBoost = (1.0 + memory.recallCount * 0.15).coerceAtMost(2.0)

        return (decay * recallBoost * freshness(memory, now) * relevance).toFloat()
    }

    /**
     * 时效系数
     *
     * 今天 1.6 / 最近几天 1.0 / 更久 0.5 / 久远 0.25。
     * 久远的记忆即使被反复提起，也不该天天挂在嘴边。
     */
    private fun freshness(memory: FishMemoryEntity, now: Long): Float {
        val age = ageDays(memory, now)
        return when {
            age < 1 -> 1.6f
            age <= RECENT -> 1.0f
            age <= OLDER -> 0.5f
            else -> 0.25f
        }
    }

    /**
     * 关键词相关度
     *
     * 中文没有空格，按词切分等于没切。这里用「中文二元组」做匹配：
     * 记忆里有「今天要考试」，上下文里有「今天」，就能算沾边。
     */
    private fun relevance(content: String, query: String): Float {
        if (query.isBlank()) return 0.5f
        val grams = gramsOf(content)
        if (grams.isEmpty()) return 0.5f
        val hits = grams.count { query.contains(it) }
        return when (hits) {
            0 -> 0.3f
            1 -> 0.8f
            2 -> 1.0f
            else -> 1.4f
        }
    }

    /** 中文二元组 + 英文单词 */
    private fun gramsOf(content: String): List<String> {
        val grams = mutableSetOf<String>()
        val cjk = content.toCharArray().filter { it.code in 0x4E00..0x9FA5 }.toCharArray()
        for (i in 0 until cjk.size - 1) {
            grams += String(cjk, i, 2)
        }
        content.split(Regex("[^A-Za-z0-9]+"))
            .filter { it.length >= 3 }
            .forEach { grams += it.lowercase() }
        return grams.toList()
    }

    /** 记忆创建至今的天数 */
    private fun ageDays(memory: FishMemoryEntity, now: Long): Double =
        (now - memory.createdAt) / DAY_MS

    /** 是否同一天（按本地时区算，不能用毫秒除法，跨时区会算错） */
    private fun isSameDay(time: Long, now: Long): Boolean {
        val a = Calendar.getInstance().apply { this.timeInMillis = time }
        val b = Calendar.getInstance().apply { this.timeInMillis = now }
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    }
}
