package com.deepfocus.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.deepfocus.app.data.local.dao.AffinityAchievementDao
import com.deepfocus.app.data.local.dao.ChatConversationDao
import com.deepfocus.app.data.local.dao.ChatDao
import com.deepfocus.app.data.local.dao.FishCatalogDao
import com.deepfocus.app.data.local.dao.FishMemoryDao
import com.deepfocus.app.data.local.dao.FishStateDao
import com.deepfocus.app.data.local.dao.FoodCatalogDao
import com.deepfocus.app.data.local.dao.FoodInventoryDao
import com.deepfocus.app.data.local.dao.FocusSessionDao
import com.deepfocus.app.data.local.dao.PersonaCardDao
import com.deepfocus.app.data.local.dao.PersonaStateDao
import com.deepfocus.app.data.local.dao.TodoDao
import com.deepfocus.app.data.local.dao.TravelJournalDao
import com.deepfocus.app.data.local.dao.UserFishDao
import com.deepfocus.app.data.local.entity.AffinityAchievementEntity
import com.deepfocus.app.data.local.entity.ChatConversationEntity
import com.deepfocus.app.data.local.entity.ChatMessageEntity
import com.deepfocus.app.data.local.entity.FishCatalogEntity
import com.deepfocus.app.data.local.entity.FishMemoryEntity
import com.deepfocus.app.data.local.entity.FishStateEntity
import com.deepfocus.app.data.local.entity.FoodCatalogEntity
import com.deepfocus.app.data.local.entity.FoodInventoryEntity
import com.deepfocus.app.data.local.entity.FocusSessionEntity
import com.deepfocus.app.data.local.entity.PersonaCardEntity
import com.deepfocus.app.data.local.entity.PersonaStateEntity
import com.deepfocus.app.data.local.entity.TodoEntity
import com.deepfocus.app.data.local.entity.TravelJournalEntity
import com.deepfocus.app.data.local.entity.UserFishEntity

/**
 * 应用主数据库
 * 包含专注会话、大肥鱼形态目录、用户大肥鱼、食物目录、食物库存、
 * 好感度成就、格言等表
 *
 * P1-4：开启 exportSchema=true，让 Room 编译期导出 JSON schema 到
 * app/schemas/，便于版本化追踪数据库结构变化、CI 校验、自动生成迁移测试。
 */
@Database(
    entities = [
        FocusSessionEntity::class,
        FishCatalogEntity::class,
        UserFishEntity::class,
        FoodCatalogEntity::class,
        FoodInventoryEntity::class,
        AffinityAchievementEntity::class,
        PersonaStateEntity::class,
        PersonaCardEntity::class,
        ChatMessageEntity::class,
        ChatConversationEntity::class,
        FishStateEntity::class,
        FishMemoryEntity::class,
        TodoEntity::class,
        TravelJournalEntity::class
    ],
    version = 18,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    /** 专注会话 DAO */
    abstract fun focusSessionDao(): FocusSessionDao

    /** 大肥鱼形态目录 DAO */
    abstract fun fishCatalogDao(): FishCatalogDao

    /** 用户大肥鱼 DAO */
    abstract fun userFishDao(): UserFishDao

    /** 食物目录 DAO */
    abstract fun foodCatalogDao(): FoodCatalogDao

    /** 食物库存 DAO */
    abstract fun foodInventoryDao(): FoodInventoryDao

    /** 好感度成就 DAO */
    abstract fun affinityAchievementDao(): AffinityAchievementDao

    /** 人格状态 DAO */
    abstract fun personaStateDao(): PersonaStateDao

    /** 自定义人格卡 DAO */
    abstract fun personaCardDao(): PersonaCardDao

    /** 聊天消息 DAO */
    abstract fun chatDao(): ChatDao

    /** 会话 DAO（开启新对话 = 归档旧会话） */
    abstract fun chatConversationDao(): ChatConversationDao

    /** 大肥鱼状态 DAO */
    abstract fun fishStateDao(): FishStateDao

    /** 大肥鱼记忆 DAO */
    abstract fun fishMemoryDao(): FishMemoryDao

    /** 今日待办 DAO */
    abstract fun todoDao(): TodoDao

    /** 旅行日记 DAO */
    abstract fun travelJournalDao(): TravelJournalDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * v1 → v2 迁移：新增 DeepFocus 相关 7 张表
         * 保留原有 4 张表数据，不破坏用户已有配置
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 大肥鱼形态目录
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS fish_catalog (
                        breedId TEXT NOT NULL PRIMARY KEY,
                        displayName TEXT NOT NULL,
                        description TEXT NOT NULL,
                        iconAsset TEXT NOT NULL,
                        idleAnimAsset TEXT NOT NULL,
                        eatAnimAsset TEXT NOT NULL,
                        happyAnimAsset TEXT NOT NULL
                    )
                """.trimIndent())

                // 用户大肥鱼（单例）
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS user_cat (
                        id INTEGER NOT NULL PRIMARY KEY,
                        breedId TEXT NOT NULL,
                        name TEXT NOT NULL,
                        affinityLevel INTEGER NOT NULL,
                        totalFeedCount INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())

                // 食物目录
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS food_catalog (
                        foodId TEXT NOT NULL PRIMARY KEY,
                        displayName TEXT NOT NULL,
                        rarity TEXT NOT NULL,
                        affinityBonus INTEGER NOT NULL,
                        dropRate REAL NOT NULL,
                        iconAsset TEXT NOT NULL
                    )
                """.trimIndent())

                // 食物库存
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS food_inventory (
                        foodId TEXT NOT NULL PRIMARY KEY,
                        count INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())

                // 反思问题
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS reflection_questions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        questionText TEXT NOT NULL,
                        `order` INTEGER NOT NULL,
                        isActive INTEGER NOT NULL,
                        isCustom INTEGER NOT NULL,
                        placeholder TEXT NOT NULL
                    )
                """.trimIndent())

                // 反思回答
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS reflection_answers (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        questionId INTEGER NOT NULL,
                        answerText TEXT NOT NULL,
                        targetApp TEXT,
                        answeredAt INTEGER NOT NULL
                    )
                """.trimIndent())

                // 好感度成就
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS affinity_achievements (
                        milestone INTEGER NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        description TEXT NOT NULL,
                        unlockedAt INTEGER
                    )
                """.trimIndent())
            }
        }

        /**
         * v2 → v3 迁移：食物目录调整（移除鸡胸肉，foodId 改为中文与图片文件名一致）
         * 清空 food_catalog 和 food_inventory，由 initSeedData 重新写入种子数据
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DELETE FROM food_inventory")
                database.execSQL("DELETE FROM food_catalog")
            }
        }

        /**
         * v3 → v4 迁移：反思问题集同步
         * SeedData.getSeedReflectionQuestions() 已移除第 3 个问题（"你这段时间的长期目标是什么？"），
         * 但 MainActivity.initSeedData 仅在反思问题表为空时写入种子，老用户仍保留 3 题。
         * 此迁移删除已被移除的第 3 题，并对剩余问题的 order 重新索引为连续值（1, 2, ...）。
         *
         * FK 行为：reflection_answers 表无 @ForeignKey 约束（实体仅持有 questionId 字段），
         * 删除问题后历史回答记录保留，questionId 成为孤儿 ID —— 作为历史数据可接受。
         *
         * 注：这两张表已在 v11 → v12 迁移中 DROP，反思问答功能早已删除。
         * 本段迁移保留是为了让 v3 的老库能顺利走到 v12。
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 删除已移除的第 3 个反思问题（按问题文本精确匹配）
                database.execSQL("DELETE FROM reflection_questions WHERE questionText = '你这段时间的长期目标是什么？'")
                // 重新索引剩余问题的 order 字段为连续值（1, 2, ...），按 (order, id) 排序。
                // 使用临时表预计算新 order，避免 UPDATE 关联子查询看到已更新行的值。
                database.execSQL("CREATE TEMP TABLE IF NOT EXISTS _rq_reorder (id INTEGER PRIMARY KEY, new_order INTEGER NOT NULL)")
                database.execSQL("DELETE FROM _rq_reorder")
                database.execSQL("""
                    INSERT INTO _rq_reorder (id, new_order)
                    SELECT r1.id, (
                        SELECT COUNT(*) + 1
                        FROM reflection_questions r2
                        WHERE r2.`order` < r1.`order`
                           OR (r2.`order` = r1.`order` AND r2.id < r1.id)
                    )
                    FROM reflection_questions r1
                """.trimIndent())
                database.execSQL("""
                    UPDATE reflection_questions
                    SET `order` = (SELECT new_order FROM _rq_reorder WHERE _rq_reorder.id = reflection_questions.id)
                """.trimIndent())
                database.execSQL("DROP TABLE IF EXISTS _rq_reorder")
            }
        }

        /**
         * v4 → v5 迁移：用户大肥鱼多实例化
         *
         * 原表结构：id 固定为 1（单例），无 isActive 字段
         * 新表结构：id 自增，新增 isActive 字段标记当前激活的大肥鱼
         *
         * 迁移逻辑：
         * 1. 创建新表 user_fish_new（id 自增、isActive 字段）
         * 2. 将旧表数据迁移到新表，旧大肥鱼设为 isActive=1（保留为当前激活）
         * 3. 删除旧表，重命名新表为 user_cat
         *
         * 这样老用户的大肥鱼数据（breedId/name/affinityLevel/totalFeedCount）完整保留，
         * 后续新建大肥鱼时会自动将旧大肥鱼设为 inactive 并插入新大肥鱼为 active。
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 创建新表（id 自增、isActive 字段）
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS user_fish_new (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        breedId TEXT NOT NULL,
                        name TEXT NOT NULL,
                        affinityLevel INTEGER NOT NULL,
                        totalFeedCount INTEGER NOT NULL,
                        isActive INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
                // 迁移旧数据：旧单例大肥鱼（id=1）设为 isActive=1，保留所有养成数据
                // 注意：v1 建的是 user_cat（旧 FocusCat 时代留下的表名），
                // 这里必须从 user_cat 读，写错了会直接 no such table 崩库。
                database.execSQL("""
                    INSERT INTO user_fish_new (breedId, name, affinityLevel, totalFeedCount, isActive, createdAt)
                    SELECT breedId, name, affinityLevel, totalFeedCount, 1, createdAt
                    FROM user_cat
                """.trimIndent())
                // 替换旧表
                database.execSQL("DROP TABLE user_cat")
                database.execSQL("ALTER TABLE user_fish_new RENAME TO user_cat")
            }
        }

        /**
         * v5 → v6 迁移：反思问题改为选择题（需求 3）
         *
         * 新增两列：
         * - questionType：题目类型（open 开放问答 / choice 选择题 / duration 使用时长选择）
         * - optionsJson：选项的 JSON 数组字符串
         *
         * 数据转换：把两条历史默认问题按其固定文案转成对应的选择题，
         * 并把原本的第二题「你今天的 todo list 还有哪些？」改为使用时长选择。
         * 用户自定义问题（isCustom=1）保持 open 形态，兼容原有开放问答 UI。
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE reflection_questions ADD COLUMN questionType TEXT NOT NULL DEFAULT 'open'"
                )
                database.execSQL(
                    "ALTER TABLE reflection_questions ADD COLUMN optionsJson TEXT NOT NULL DEFAULT ''"
                )
                // 默认第 1 题 → 选择题
                database.execSQL("""
                    UPDATE reflection_questions
                    SET questionType = 'choice',
                        optionsJson = '["消遣一下","完成具体任务","只是想看看","其他"]'
                    WHERE isCustom = 0 AND questionText = '你进入这个 app 是来做什么的？'
                """.trimIndent())
                // 默认第 2 题 → 使用时长选择
                database.execSQL("""
                    UPDATE reflection_questions
                    SET questionType = 'duration',
                        questionText = '这次打算使用多久？',
                        optionsJson = '["5 分钟","15 分钟","30 分钟","1 小时"]'
                    WHERE isCustom = 0 AND questionText = '你今天的 todo list 还有哪些？'
                """.trimIndent())
            }
        }

        /**
         * v6 → v7 迁移：新增大肥鱼格言表（需求 1）
         *
         * 退出专注时不发放大白饭，改为随机展示一句大肥鱼格言；
         * 格言内容由用户自行编写，可在设置中增删改。
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS quotes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        text TEXT NOT NULL,
                        `order` INTEGER NOT NULL,
                        isActive INTEGER NOT NULL,
                        isCustom INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        /**
         * v7 → v8 迁移：新增 AI 对话与大肥鱼状态相关表
         *
         * - persona：大肥鱼人格卡（单条 id=1）
         * - chat_messages：对话记录（统计页改为对话页后的载体）
         * - fish_state：饱腹度/心情/精力/睡眠状态（单条 id=1）
         * - books：大肥鱼阅读的书籍
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS persona (
                        id INTEGER NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        identity TEXT NOT NULL,
                        traits TEXT NOT NULL,
                        speechStyle TEXT NOT NULL,
                        catchphrases TEXT NOT NULL,
                        backstory TEXT NOT NULL,
                        taboos TEXT NOT NULL,
                        userAlias TEXT NOT NULL,
                        temperature REAL NOT NULL,
                        enabled INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS chat_messages (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        role TEXT NOT NULL,
                        content TEXT NOT NULL,
                        kind TEXT NOT NULL,
                        action TEXT,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS fish_state (
                        id INTEGER NOT NULL PRIMARY KEY,
                        satiety INTEGER NOT NULL,
                        mood INTEGER NOT NULL,
                        energy INTEGER NOT NULL,
                        isSleeping INTEGER NOT NULL,
                        sleepStartedAt INTEGER NOT NULL,
                        totalSleepMillis INTEGER NOT NULL,
                        lastProactiveAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS books (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        author TEXT,
                        sourceUri TEXT NOT NULL,
                        fileName TEXT NOT NULL,
                        fileType TEXT NOT NULL,
                        contentPath TEXT,
                        totalPages INTEGER NOT NULL,
                        currentPage INTEGER NOT NULL,
                        isFinished INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        /**
         * v8 → v9 迁移：新增大肥鱼记忆表 + 补 persona.proactiveMotivation 缺列
         *
         * ## 1. 新增大肥鱼记忆表
         *
         * kind：RELATION（关于用户）/ TASK（一起做过的事）
         * status：CANDIDATE（待用户确认，不注入）/ CONFIRMED（注入）
         * isPinned：长期事实，每轮固定注入
         *
         * ## 2. 补 persona.proactiveMotivation 缺列
         *
         * v7→v8 建 persona 表时少建了这一列，v8→v9 也没补，
         * 于是从 v8 升上来的库，persona 表比实体少一列 ——
         * Room 启动时 schema 校验直接失败，App 开不了。
         * （MIGRATION_11_12 也补过同一列，但那只救得了 v11 以下的库，
         *   v8→v9 这一跳本身仍然是不自洽的。）
         *
         * 同样用「重建 + 搬数据」而不是 ALTER TABLE ADD COLUMN：
         * ADD COLUMN 必须带 DEFAULT，而 Room 校验会比对 dflt_value，
         * 带默认值会和全新安装的表结构对不上。
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS fish_memories (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        kind TEXT NOT NULL,
                        content TEXT NOT NULL,
                        status TEXT NOT NULL,
                        isPinned INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        lastRecalledAt INTEGER NOT NULL,
                        recallCount INTEGER NOT NULL
                    )
                """.trimIndent())

                if (!hasColumn(database, "persona", "proactiveMotivation")) {
                    database.execSQL("""
                        CREATE TABLE IF NOT EXISTS persona_new (
                            id INTEGER NOT NULL PRIMARY KEY,
                            name TEXT NOT NULL,
                            identity TEXT NOT NULL,
                            traits TEXT NOT NULL,
                            speechStyle TEXT NOT NULL,
                            catchphrases TEXT NOT NULL,
                            backstory TEXT NOT NULL,
                            taboos TEXT NOT NULL,
                            userAlias TEXT NOT NULL,
                            proactiveMotivation TEXT NOT NULL,
                            temperature REAL NOT NULL,
                            enabled INTEGER NOT NULL,
                            updatedAt INTEGER NOT NULL
                        )
                    """.trimIndent())
                    database.execSQL("""
                        INSERT INTO persona_new (
                            id, name, identity, traits, speechStyle, catchphrases,
                            backstory, taboos, userAlias, proactiveMotivation,
                            temperature, enabled, updatedAt
                        )
                        SELECT id, name, identity, traits, speechStyle, catchphrases,
                               backstory, taboos, userAlias,
                               '你注意到他今天摸鱼摸得有点久了，想吐槽他一句。你刚读完书里一段有感觉的，想跟他分享。你有点饿了，想要一口白饭。太久没人理你了，你有点闷。想起来他上次提过的一件事，想问一句。',
                               temperature, enabled, updatedAt
                        FROM persona
                    """.trimIndent())
                    database.execSQL("DROP TABLE persona")
                    database.execSQL("ALTER TABLE persona_new RENAME TO persona")
                }
            }
        }

        /**
         * v9 → v10 迁移：删除「约束应用」相关表
         *
         * 产品决策：移除约束应用（拦截指定 App）功能及其拦截弹窗链路，
         * 因此删除以下三张已无用的表：
         * - blocked_apps：约束应用名单
         * - quiz_questions：拦截问答题库（选择题库）
         * - quiz_history：拦截问答历史
         *
         * 其余表（专注会话、大肥鱼养成、格言、聊天等）保持不变，用户数据完整保留。
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP TABLE IF EXISTS blocked_apps")
                database.execSQL("DROP TABLE IF EXISTS quiz_questions")
                database.execSQL("DROP TABLE IF EXISTS quiz_history")
            }
        }

        /**
         * v10 → v11 迁移：修正表名 user_cat → user_fish
         *
         * v1 时代（FocusCat）建的表叫 `user_cat`，
         * 但实体 [UserFishEntity] 和 [UserFishDao] 用的都是 `user_fish`。
         * 中间的迁移链（v4→v5）虽然建了 `user_fish_new`，
         * 最后却 `RENAME TO user_cat` —— 于是表名一直是错的，
         * Room 启动时表校验不通过，App 直接崩。
         *
         * 这里补上改名，让库结构和实体对齐。
         */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 改名：user_cat → user_fish
                database.execSQL("ALTER TABLE user_cat RENAME TO user_fish")
            }
        }

        /**
         * v11 → v12 迁移：删死表 + 补 persona 缺列
         *
         * ## 1. 删掉反思问答两张死表
         *
         * 产品决策：「约束应用」和反思问答链路早已整块删除
         * （见 project_rules.md §0.1），但 `reflection_questions` /
         * `reflection_answers` 两张表一直留在库里：
         * 实体、DAO、Repository、迁移 SQL 全在，唯独没有任何 UI 消费者。
         *
         * 这里把表删掉，让库结构与实体列表一致。
         * 用户在这两张表里没有可迁移到别处的数据（问答记录本身已无意义），
         * 直接 DROP 即可。
         *
         * ## 2. 补 persona.proactiveMotivation 缺列
         *
         * `PersonaEntity` 有 `proactiveMotivation` 字段（主动开口的动机文案），
         * 但 v7→v8 建 persona 表时没建这一列，后面三段迁移也没补 ——
         * 于是从 v8 之前升上来的库，persona 表比实体少一列，
         * Room 启动时 schema 校验直接失败，App 开不了。
         *
         * 用「重建 + 搬数据」而不是 `ALTER TABLE ADD COLUMN`：
         * ADD COLUMN 必须带 DEFAULT，而 Room 校验会比对 dflt_value，
         * 带默认值会和全新安装的表结构对不上。
         * 重建出来的表结构和 Room 自己生成的 CREATE TABLE 完全一致。
         *
         * persona 是单行表（id 固定 1），重建丢不了东西。
         */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP TABLE IF EXISTS reflection_answers")
                database.execSQL("DROP TABLE IF EXISTS reflection_questions")

                // 已经有这一列就不用动（新装的库从 v8 起就带着它）
                if (!hasColumn(database, "persona", "proactiveMotivation")) {
                    database.execSQL("""
                        CREATE TABLE IF NOT EXISTS persona_new (
                            id INTEGER NOT NULL PRIMARY KEY,
                            name TEXT NOT NULL,
                            identity TEXT NOT NULL,
                            traits TEXT NOT NULL,
                            speechStyle TEXT NOT NULL,
                            catchphrases TEXT NOT NULL,
                            backstory TEXT NOT NULL,
                            taboos TEXT NOT NULL,
                            userAlias TEXT NOT NULL,
                            proactiveMotivation TEXT NOT NULL,
                            temperature REAL NOT NULL,
                            enabled INTEGER NOT NULL,
                            updatedAt INTEGER NOT NULL
                        )
                    """.trimIndent())
                    database.execSQL("""
                        INSERT INTO persona_new (
                            id, name, identity, traits, speechStyle, catchphrases,
                            backstory, taboos, userAlias, proactiveMotivation,
                            temperature, enabled, updatedAt
                        )
                        SELECT id, name, identity, traits, speechStyle, catchphrases,
                               backstory, taboos, userAlias,
                               '你注意到他今天摸鱼摸得有点久了，想吐槽他一句。你刚读完书里一段有感觉的，想跟他分享。你有点饿了，想要一口白饭。太久没人理你了，你有点闷。想起来他上次提过的一件事，想问一句。',
                               temperature, enabled, updatedAt
                        FROM persona
                    """.trimIndent())
                    database.execSQL("DROP TABLE persona")
                    database.execSQL("ALTER TABLE persona_new RENAME TO persona")
                }
            }
        }

        /**
         * v12 → v13 迁移：人格卡拆成「底稿 + 覆盖层 + 自定义包」
         *
         * 旧模型：persona 表单行存「当前生效的人格」，三档手动切换。
         * 新模型（见 docs/adr/0001）：
         * - `persona_cards` —— 用户自定义人格卡包（新增）
         * - `persona_state` —— 用户自有字段 + 覆盖层 + 底稿选择（新增）
         * - `persona` —— 删除，它的字段全部搬进上面两张表
         *
         * 旧库里 persona 行的性格字段是三档人格卡里选中的那一套。
         * 它们本质上是「用户选过的底稿」，直接搬到覆盖层里当覆盖值 ——
         * 这样老用户升级后不会突然变成陌生人，他们的自定义被继承下来。
         *
         * 覆盖层的字段允许 NULL（= 沿用底稿），所以不能用
         * `ALTER TABLE ADD COLUMN`（带 DEFAULT 会与 Room 校验对不上），
         * 只能用「新建 + 搬数据」。
         */
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 自定义人格卡包
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS persona_cards (
                        id TEXT NOT NULL PRIMARY KEY,
                        displayName TEXT NOT NULL,
                        tagline TEXT NOT NULL,
                        identity TEXT NOT NULL,
                        traits TEXT NOT NULL,
                        speechStyle TEXT NOT NULL,
                        catchphrases TEXT NOT NULL,
                        backstory TEXT NOT NULL,
                        taboos TEXT NOT NULL,
                        proactiveMotivation TEXT NOT NULL,
                        temperature REAL NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())

                // 状态行：用户自有字段 + 覆盖层（可空 = 沿用底稿）+ 底稿选择
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS persona_state (
                        id INTEGER NOT NULL PRIMARY KEY,
                        baseCardId TEXT,
                        selectedTierThreshold INTEGER NOT NULL,
                        identity TEXT,
                        traits TEXT,
                        speechStyle TEXT,
                        catchphrases TEXT,
                        backstory TEXT,
                        taboos TEXT,
                        proactiveMotivation TEXT,
                        temperature REAL,
                        userAlias TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())

                // 把旧 persona 行搬进状态行。
                // 纯 SQL 的 INSERT ... SELECT —— 不用 Cursor：
                // Cursor 一旦离开 use{} 块就已关闭，再读列会抛异常。
                // 旧 persona 表里可能根本没有行（用户从没改过人格），
                // INSERT ... SELECT 查不到就插 0 行，状态行由运行时按需创建。
                // 旧表的 name 不搬 —— 鱼名的真源是 user_fish.name，
                // 人格状态行是全局单行，存不下多只鱼各自的名字。
                database.execSQL("""
                    INSERT OR REPLACE INTO persona_state (
                        id, baseCardId, selectedTierThreshold,
                        identity, traits, speechStyle, catchphrases, backstory,
                        taboos, proactiveMotivation, temperature,
                        userAlias, updatedAt
                    )
                    SELECT
                        1, NULL, 0,
                        identity, traits, speechStyle, catchphrases, backstory,
                        taboos, proactiveMotivation, temperature,
                        userAlias, updatedAt
                    FROM persona
                    WHERE id = 1
                """.trimIndent())

                database.execSQL("DROP TABLE IF EXISTS persona")
            }
        }

        /**
         * v13 → v14 迁移：删「心情（mood）」维度 + 加 lastFedAt + 食物缩为只有大白饭
         *
         * ## 1. fish_state：删 mood 列，加 lastFedAt 列
         *
         * 产品决策：**没有心情（mood）这个维度**（见 project_rules.md §1.2.1）。
         * 情绪由好感度档位和当下情境推导，不单独存一个可正可负的数值 ——
         * 存了的话，「喂一次就心情 +10」会变成唯一的互动方式。
         *
         * 新增 `lastFedAt`：**「饿」的判定只看距上次喂食是否超过 24 小时**，
         * 与饱腹度数值无关。老数据没有这个时间戳，迁移后统一填 0（= 从没喂过 = 饿）。
         *
         * 删列同样不能用 `ALTER TABLE DROP COLUMN`（低版本 SQLite 不支持），
         * 只能用「新建 + 搬数据」。
         *
         * ## 2. food_catalog：删 rarity / affinityBonus / dropRate 三列
         *
         * 食物**只有大白饭一种**，作用只有饱腹，**不涨好感度**：
         * - `affinityBonus`（好感度加成）—— 语义作废，喂食不再涨好感度
         * - `dropRate`（掉落概率）—— 语义作废，没有随机抽取这回事了
         * - `rarity`（稀有度）—— 语义作废，只有一档
         *
         * 库存表 `food_inventory` 不动 —— 用户背包里可能还留着已下架食物的存货。
         */
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 1. fish_state：删 mood，加 lastFedAt
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS fish_state_new (
                        id INTEGER NOT NULL PRIMARY KEY,
                        satiety INTEGER NOT NULL,
                        energy INTEGER NOT NULL,
                        isSleeping INTEGER NOT NULL,
                        sleepStartedAt INTEGER NOT NULL,
                        totalSleepMillis INTEGER NOT NULL,
                        lastFedAt INTEGER NOT NULL,
                        lastProactiveAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
                database.execSQL("""
                    INSERT INTO fish_state_new (
                        id, satiety, energy, isSleeping, sleepStartedAt,
                        totalSleepMillis, lastFedAt, lastProactiveAt, updatedAt
                    )
                    SELECT
                        id, satiety, energy, isSleeping, sleepStartedAt,
                        totalSleepMillis, 0, lastProactiveAt, updatedAt
                    FROM fish_state
                """.trimIndent())
                database.execSQL("DROP TABLE fish_state")
                database.execSQL("ALTER TABLE fish_state_new RENAME TO fish_state")

                // 2. food_catalog：删三列作废字段
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS food_catalog_new (
                        foodId TEXT NOT NULL PRIMARY KEY,
                        displayName TEXT NOT NULL,
                        iconAsset TEXT NOT NULL
                    )
                """.trimIndent())
                database.execSQL("""
                    INSERT INTO food_catalog_new (foodId, displayName, iconAsset)
                    SELECT foodId, displayName, iconAsset FROM food_catalog
                    WHERE foodId = '大白饭'
                """.trimIndent())
                database.execSQL("DROP TABLE food_catalog")
                database.execSQL("ALTER TABLE food_catalog_new RENAME TO food_catalog")

                // 3. 清掉已下架食物的存货
                // 冻干 / 酸奶 / 罐头 的图已经从 assets/foods 删了，
                // 背包里留着它们会渲染成空白格子，且点进去喂不了。
                database.execSQL("DELETE FROM food_inventory WHERE foodId != '大白饭'")
            }
        }

        /**
         * ## v14 → v15：删两样不在设计里的东西
         *
         * - `quotes` 表（大肥鱼格言）—— 那是「专注猫」的功能，不属于 DeepFocus
         * - `fish_state.energy`（精力）—— FocusCat 猫模型的遗留，
         *   用户从没设计过它，也没有可见入口。删掉后睡觉判据改成
         *   「长时间没互动就睡」，不再依赖一个看不见也设不了的数值。
         *
         * 删列必须重建表 —— SQLite 不支持 `ALTER TABLE DROP COLUMN`（3.35 以前）。
         */
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 1. fish_state：删 energy 列
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS fish_state_new (
                        id INTEGER NOT NULL PRIMARY KEY,
                        satiety INTEGER NOT NULL,
                        isSleeping INTEGER NOT NULL,
                        sleepStartedAt INTEGER NOT NULL,
                        totalSleepMillis INTEGER NOT NULL,
                        lastFedAt INTEGER NOT NULL,
                        lastProactiveAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
                database.execSQL("""
                    INSERT INTO fish_state_new (
                        id, satiety, isSleeping, sleepStartedAt,
                        totalSleepMillis, lastFedAt, lastProactiveAt, updatedAt
                    )
                    SELECT
                        id, satiety, isSleeping, sleepStartedAt,
                        totalSleepMillis, lastFedAt, lastProactiveAt, updatedAt
                    FROM fish_state
                """.trimIndent())
                database.execSQL("DROP TABLE fish_state")
                database.execSQL("ALTER TABLE fish_state_new RENAME TO fish_state")

                // 2. quotes 表：整张删掉
                database.execSQL("DROP TABLE IF EXISTS quotes")
            }
        }

        /**
         * v15 → v16：新增今日待办表
         *
         * 2026-11-18 用户要求：「增加一个 todo 菜单栏选项，
         * 这里可以添加今日 todo list，大肥鱼会知道用户今天的计划」。
         *
         * 列定义必须跟 Room 导出的 schema **逐字一致**：
         * - 不能加 `DEFAULT 0` —— Room 校验会比对 `dflt_value`，
         *   实体里没写 `@ColumnInfo(defaultValue=...)` 就是 'undefined'。
         * - 不能自己加索引 —— 实体没声明 `@Index`，schema 里 indices 是空的，
         *   多出来的索引同样会让校验失败（迁移后 App 直接开不了）。
         */
        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS todos (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        content TEXT NOT NULL,
                        dayKey TEXT NOT NULL,
                        done INTEGER NOT NULL,
                        doneAt INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        /**
         * ## v16 → v17：陪读下线，赛博旅游上线
         *
         * - `books` 表整个删掉 —— 陪读模块下线（用户决定：直接去旅游）
         * - `chat_messages` 加 `imageUri` 列 —— 聊天可以发图了
         * - 新建 `travel_journals` 表 —— 旅行日记本（游历本的数据源）
         *
         * 列定义必须跟 Room 导出的 schema **逐字一致**：
         * - 不能加 `DEFAULT 0` —— Room 校验会比对 `dflt_value`，
         *   实体里没写 `@ColumnInfo(defaultValue=...)` 就是 'undefined'。
         * - 不能自己加索引 —— 实体没声明 `@Index`，schema 里 indices 是空的，
         *   多出来的索引同样会让校验失败（迁移后 App 直接开不了）。
         */
        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 1. books 表整张删掉
                database.execSQL("DROP TABLE IF EXISTS books")

                // 2. chat_messages 加 imageUri 列（可空 → 不需要 DEFAULT）
                database.execSQL(
                    "ALTER TABLE chat_messages ADD COLUMN imageUri TEXT"
                )

                // 3. travel_journals 新表
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS travel_journals (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        destination TEXT NOT NULL,
                        content TEXT NOT NULL,
                        mood TEXT NOT NULL,
                        outfitId TEXT NOT NULL,
                        imagePath TEXT,
                        imagePrompt TEXT,
                        isFavorite INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        /**
         * 17 → 18：会话体系
         *
         * 三件事：
         * 1. 新建 `chat_conversations` 表 —— 一段对话 = 一个会话。
         * 2. `chat_messages` 加 `conversationId`（可空 → 不需要 DEFAULT）
         * 3. `fish_memories` 加 `conversationId`（同上）
         *    —— 用户删掉一段对话时，这段对话里抽出来的记忆要跟着删，
         *    否则她一边说「不记得了」一边又提起被删掉的事。
         * 4. `travel_journals` 加 `photoIdea` —— 她自己设计的画面构想，
         *    之前生图时明明算出来了却没落库，配图时只能用通用描述。
         *
         * 老消息、老记忆的 conversationId 都是 NULL ——
         * 「还没归入会话」的状态在应用层等价于「属于当前会话」。
         */
        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS chat_conversations (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        summary TEXT,
                        compactedUpToId INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        archived INTEGER NOT NULL
                    )
                """.trimIndent())

                if (!hasColumn(database, "chat_messages", "conversationId")) {
                    database.execSQL(
                        "ALTER TABLE chat_messages ADD COLUMN conversationId INTEGER"
                    )
                }

                if (!hasColumn(database, "fish_memories", "conversationId")) {
                    database.execSQL(
                        "ALTER TABLE fish_memories ADD COLUMN conversationId INTEGER"
                    )
                }

                if (!hasColumn(database, "travel_journals", "photoIdea")) {
                    database.execSQL(
                        "ALTER TABLE travel_journals ADD COLUMN photoIdea TEXT"
                    )
                }

                // 老消息归到 id=1 的「最早的那段对话」——
                // 否则升级后历史记录会「消失」（它们没有 conversationId）。
                database.execSQL(
                    """
                    INSERT OR IGNORE INTO chat_conversations
                        (id, title, summary, compactedUpToId, createdAt, updatedAt, archived)
                    VALUES (1, '最早的那段对话', NULL, 0, 0, 0, 0)
                    """.trimIndent()
                )
                database.execSQL(
                    "UPDATE chat_messages SET conversationId = 1 WHERE conversationId IS NULL"
                )
            }
        }

        /**
         * 表里是否已有某一列
         *
         * SQLite 的 `ALTER TABLE ADD COLUMN` 不支持 `IF NOT EXISTS`，
         * 补列前必须先查一遍，否则重复执行会直接报错。
         */
        private fun hasColumn(
            database: SupportSQLiteDatabase,
            table: String,
            column: String
        ): Boolean {
            database.query("PRAGMA table_info(`$table`)").use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex) == column) return true
                }
            }
            return false
        }

        /**
         * 获取数据库单例实例
         * @param context 上下文
         * @return 数据库实例
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "deepfocus.db"
                )
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
                        MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11,
                        MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15,
                        MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18
                    )
                    // 仅在数据库降级时销毁数据，升级时通过 Migration 保留用户数据
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
