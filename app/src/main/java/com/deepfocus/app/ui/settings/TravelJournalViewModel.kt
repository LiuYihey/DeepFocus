package com.deepfocus.app.ui.settings

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.deepfocus.app.ai.ImageGenClient
import com.deepfocus.app.ai.ImageGenConfigStore
import com.deepfocus.app.data.FishOutfitStore
import com.deepfocus.app.data.local.dao.TravelJournalDao
import com.deepfocus.app.data.local.entity.TravelJournalEntity
import com.deepfocus.app.util.MediaStoreSaver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * 游历本 UI 状态
 */
data class TravelJournalUiState(
    /** 全部旅行日记（新的在前） */
    val journals: List<TravelJournalEntity> = emptyList(),
    /** 正在为哪一篇画相片（null = 没在画） */
    val paintingId: Long? = null,
    /** 提示消息（一次性） */
    val message: String? = null,
    /** 是否配了生图 API */
    val imageApiReady: Boolean = false,
    /** 是否正在出门（调试触发时用） */
    val isTraveling: Boolean = false
)

/**
 * 游历本 ViewModel
 *
 * 管两件事：
 * 1. 列日记（数据来自 `travel_journals`）
 * 2. 手动给某篇日记画相片（带当前装扮的参考图），并允许存进系统相册
 */
@HiltViewModel
class TravelJournalViewModel @Inject constructor(
    private val travelJournalDao: TravelJournalDao,
    private val imageGenClient: ImageGenClient,
    private val imageGenConfigStore: ImageGenConfigStore,
    private val outfitStore: FishOutfitStore,
    private val travelService: com.deepfocus.app.ai.TravelService,
    application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(TravelJournalUiState())
    val uiState: StateFlow<TravelJournalUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            travelJournalDao.observeAll().collect { list ->
                _uiState.update { it.copy(journals = list) }
            }
        }
        viewModelScope.launch {
            imageGenConfigStore.config.collect { cfg ->
                _uiState.update { it.copy(imageApiReady = cfg.isValid && cfg.enabled) }
            }
        }
    }

    /**
     * 给一篇日记画一张旅行相片
     *
     * 流程：她自己定画面（自拍 / 他拍 / 漫画）→ 带当前装扮的参考图去生成
     * → 存本地 → 回填日记。
     */
    fun paint(journal: TravelJournalEntity) {
        if (_uiState.value.paintingId != null) return

        val config = imageGenConfigStore.config.value
        if (!config.isValid || !config.enabled) {
            _uiState.update { it.copy(message = "先在设置里填好生图 API") }
            return
        }

        _uiState.update { it.copy(paintingId = journal.id) }

        viewModelScope.launch {
            try {
                val outfit = outfitStore.current()
                val prompt = buildPhotoPrompt(journal)
                val reference = imageGenClient.readAssetBytes(outfit.referenceAssetPath)
                val mime = if (outfit.referenceAssetPath.endsWith(".png")) "image/png" else "image/jpeg"

                val bytes = imageGenClient.generate(
                    config = config,
                    prompt = prompt,
                    referenceImage = reference,
                    referenceMime = mime,
                    aspectRatio = "3:4",
                    area = "1K"
                )

                val savedPath = withContext(Dispatchers.IO) {
                    val dir = File(getApplication<Application>().filesDir, "travel_images")
                    if (!dir.exists()) dir.mkdirs()
                    val file = File(dir, "travel_${journal.id}_${System.currentTimeMillis()}.jpg")
                    file.writeBytes(bytes)
                    file.absolutePath
                }

                travelJournalDao.updateImage(
                    id = journal.id,
                    imagePath = savedPath,
                    imagePrompt = prompt
                )
                _uiState.update { it.copy(paintingId = null, message = "相片画好了") }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(paintingId = null, message = e.message ?: "生图失败")
                }
            }
        }
    }

    /** 把配图存进系统相册 */
    fun saveToAlbum(journal: TravelJournalEntity) {
        val path = journal.imagePath ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val file = File(path)
                if (!file.exists()) return@withContext "图片已经不在了"
                MediaStoreSaver.saveImage(
                    context = getApplication(),
                    bytes = file.readBytes(),
                    fileName = "deepfocus_travel_${journal.id}.jpg",
                    mimeType = "image/jpeg"
                )
            }
            _uiState.update {
                it.copy(message = if (result == null) "已存进相册" else "存相册失败：$result")
            }
        }
    }

    /** 收藏 / 取消收藏 */
    fun toggleFavorite(journal: TravelJournalEntity) {
        viewModelScope.launch {
            travelJournalDao.setFavorite(journal.id, !journal.isFavorite)
        }
    }

    /**
     * 删掉一篇日记
     *
     * 数据库里的行删掉还不够 —— 配图文件还躺在 `filesDir/travel_images` 里，
     * 那也是这趟旅行的一部分，一起清掉，磁盘不留垃圾。
     *
     * 删完之后她不会再提这趟旅行：她的「最近一次旅行」上下文
     * 是从 `travel_journals` 表读的，行没了，上下文里就没了。
     */
    suspend fun deleteJournal(journal: TravelJournalEntity) {
        travelJournalDao.delete(journal.id)
        deleteImageFile(journal.imagePath)
        _uiState.update { it.copy(message = "「${journal.destination}」这篇日记已经删掉了") }
    }

    /**
     * 删掉磁盘上的配图文件
     *
     * 文件可能已经不在了（清过缓存 / 手动删过），所以一律容错。
     */
    private suspend fun deleteImageFile(path: String?) {
        if (path.isNullOrBlank()) return
        withContext(Dispatchers.IO) {
            val file = resolveImageFile(path)
            if (file.exists()) file.delete()

            // 顺手清掉空的 travel_images 目录；非空的话留着，别删别人的文件
            val dir = File(getApplication<Application>().filesDir, "travel_images")
            if (dir.isDirectory && dir.list()?.isEmpty() == true) {
                dir.delete()
            }
        }
    }

    /** 配图路径有两种写法：绝对路径，或相对 filesDir 的相对路径 */
    private fun resolveImageFile(path: String): File {
        val file = File(path)
        return if (file.isAbsolute) file else File(getApplication<Application>().filesDir, path)
    }

    /** 消费一次性提示 */
    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    /**
     * 构造生图提示词
     *
     * 一条铁律：**人物画风不变，别的都自由**。
     *
     * - **人物**（长相、服饰、画风）完全照参考图来。参考图是什么画风，
     *   出来就是什么画风 —— 这次参考图是赛璐璐二次元，以后换成别的画风，
     *   prompt 也不该把它按死成「日系二次元」。
     * - **场景 / 故事 / 镜头 / 氛围**全部放开让她发挥。
     *
     * 之前把画风写死成「日系二次元插画」，结果就是模型在参考图之外
     * 重新挑了一个画风，出来的东西跟参考图对不上 —— 那才是恐怖谷的根因。
     * 正确做法是把画风**交还给参考图**，prompt 一个字都不描述角色长相
     * （见项目规则 §3.2）。
     *
     * 另外把她在旅行时自己设计的「这一秒拍什么」（[TravelJournalEntity.photoIdea]）用上 ——
     * 那是她的构思，要尊重。
     */
    private fun buildPhotoPrompt(journal: TravelJournalEntity): String {
        val idea = journal.photoIdea?.trim().orEmpty()
        val ideaLine = if (idea.isNotEmpty()) {
            "她自己在旅行时想好的这一秒是：「$idea」——照她想的那样画。"
        } else {
            "这一秒具体拍什么由你决定。"
        }

        return """
【人物】
人物完全以参考图为准：参考图里是什么画风，画面里就是什么画风；
长相、发型、服饰、配色也都照着参考图来，不要重新设计角色，不要改动任何设定。

【不要混搭】
整张图要是同一种画风。不能人物是参考图那种画风、背景却换成另一种
（比如人物是插画、背景却是写实照片质感，或者反过来）——
这种半拼半凑最容易出恐怖谷，看着就是不对劲。
背景、光线、景深都跟着人物的画风走。

【场景】
她正在${journal.destination}旅行，这一刻的氛围是「${journal.mood}」，画面里要有当地特色。
场景怎么搭、镜头怎么取、光线怎么打，你自己发挥，只要跟人物的画风搭得上就行。

【形式】
画面形式由她自己挑一种最合适的：自拍、别人帮她拍、或者画成漫画分镜。
哪种最贴切就选哪种，不用有偏好。

【这一秒拍什么】
$ideaLine

【日记里写的事】
${journal.content.take(220)}
""".trimIndent()
    }

    /** 解码本地图片（卡片展示用） */
    companion object {
        fun decodeImage(path: String?): Bitmap? {
            if (path.isNullOrBlank()) return null
            return runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
        }
    }
}
