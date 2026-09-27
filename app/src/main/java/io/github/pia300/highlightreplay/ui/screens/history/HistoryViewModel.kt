package io.github.pia300.highlightreplay.ui.screens.history

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.pia300.highlightreplay.R
import io.github.pia300.highlightreplay.data.LanguagePrefs
import io.github.pia300.highlightreplay.data.TagCoverage
import io.github.pia300.highlightreplay.data.TagStore
import io.github.pia300.highlightreplay.data.VideoTag
import io.github.pia300.highlightreplay.service.session.SessionEvent
import io.github.pia300.highlightreplay.service.session.SessionStateStore
import io.github.pia300.highlightreplay.ui.ToastCenter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * 历史列表的排序方式，每个条目对应一个本地化标签资源。
 */
enum class HistorySort(@param:StringRes val labelRes: Int) {
    DATE_DESC(R.string.history_sort_date_desc),
    DATE_ASC(R.string.history_sort_date_asc),
    NAME_ASC(R.string.history_sort_name_asc),
    NAME_DESC(R.string.history_sort_name_desc),
    SIZE_DESC(R.string.history_sort_size_desc),
    SIZE_ASC(R.string.history_sort_size_asc)
}

/**
 * 一条历史视频的不可变数据，含展示与操作所需的全部字段。
 */
@androidx.compose.runtime.Immutable
data class VideoItem(
    val uri: Uri,
    val id: Long,
    val displayName: String,
    val dateAdded: Long,
    val durationMs: Long,
    val sizeBytes: Long,

    val sizeText: String,
    val durationText: String,
    val dateText: String
)

data class HistoryUiState(
    val videos: List<VideoItem> = emptyList(),
    val displayVideos: List<VideoItem> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val sort: HistorySort = HistorySort.DATE_DESC,
    val query: String = "",

    val tags: List<VideoTag> = emptyList(),

    val tagMap: Map<Long, List<VideoTag>> = emptyMap()
)

enum class RenameResult {
    SUCCESS,

    UNCHANGED,

    FAILED
}

/**
 * 历史页 ViewModel：查询视频并管理排序、搜索与标签数据。
 */
class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "HistoryViewModel"
    }

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    /** MediaStore 与文件读写入口；持有应用 Context，可长于界面生命周期。 */
    private val mediaRepository = HistoryMediaRepository(application)

    private var loadJob: Job? = null

    private var filterJob: Job? = null

    private var tagJob: Job? = null

    init {

        viewModelScope.launch {
            // drop(1) 跳过初始计数值，避免启动时触发一次重算。
            TagStore.changeCount.drop(1).collect {
                recomputeTagData()
            }
        }

        // 保存完成时自动刷新列表：走显式事件通道，不对 StateFlow 做下降沿检测。
        viewModelScope.launch {
            SessionStateStore.events.collect { event ->
                if (event == SessionEvent.SaveFinished) refresh()
            }
        }
    }

    private fun recomputeTagData() {
        tagJob?.cancel()
        val videosSnapshot = _uiState.value.videos
        tagJob = viewModelScope.launch(Dispatchers.Default) {
            val app = getApplication<Application>()
            val tags = TagStore.getTags(app)
            val tagMap = TagStore.getTagsForVideos(app, videosSnapshot.map { it.id })
            _uiState.update {
                // 仅当视频列表未变化时发布标签数据，避免旧任务覆盖新列表。
                if (it.videos === videosSnapshot) it.copy(tags = tags, tagMap = tagMap) else it
            }
        }
        // 标签数据变化后重算显示列表（搜索会按标签匹配）。
        scheduleDisplayRecompute()
    }

    /**
     * 在 IO 线程执行标签写操作：TagStore 每次都会全量解析并重新序列化 JSON，且各入口均为同步方法，
     * 直接在 Compose 点击回调里调用会阻塞主线程（大标签量下可能 ANR）。写入后由 changeCount 触发重算。
     *
     * 写盘失败（存储写满、卷只读等）时提示用户：TagStore 的内存缓存已更新，界面会照常显示改动，
     * 但重启后改动会丢失，不提示则用户无从察觉。
     */
    fun mutateTags(block: (Application) -> Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val stored = try {
                block(getApplication())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Tag mutation failed", e)
                false
            }
            if (!stored) {
                val app = getApplication<Application>()
                ToastCenter.show(app, R.string.history_tag_save_failed, Toast.LENGTH_SHORT)
            }
        }
    }

    /** 计算所选视频的标签覆盖状态（IO 线程），供批量打标签对话框初始化选中态。 */
    suspend fun tagCoverages(
        videoIds: List<Long>,
        tagIds: List<String>
    ): Map<String, TagCoverage> = withContext(Dispatchers.IO) {
        TagStore.tagCoverages(getApplication(), videoIds, tagIds)
    }

    fun setSort(sort: HistorySort) {
        _uiState.update { it.copy(sort = sort) }
        scheduleDisplayRecompute()
    }

    fun setQuery(query: String) {
        _uiState.update { it.copy(query = query) }

        scheduleDebouncedDisplayRecompute()
    }

    private fun scheduleDisplayRecompute() = scheduleDisplayRecomputeWithDelay(Duration.ZERO)

    private fun scheduleDebouncedDisplayRecompute() =
        scheduleDisplayRecomputeWithDelay(200.milliseconds)

    private fun scheduleDisplayRecomputeWithDelay(delayDuration: Duration) {
        filterJob?.cancel()
        val snapshot = _uiState.value
        val (videos, sort, query) = Triple(snapshot.videos, snapshot.sort, snapshot.query)
        filterJob = viewModelScope.launch(Dispatchers.Default) {
            if (delayDuration > Duration.ZERO) delay(delayDuration)
            val filtered = filterInternal(videos, sort, query)
            _uiState.update {
                // 仅当视频、排序与关键词均未变化时发布结果，避免旧任务覆盖新状态。
                if (it.videos === videos && it.sort == sort && it.query == query) {
                    it.copy(displayVideos = filtered)
                } else {
                    it
                }
            }
        }
    }

    private fun filterInternal(
        videos: List<VideoItem>,
        sort: HistorySort,
        query: String
    ): List<VideoItem> = filterAndSortVideos(videos, sort, query) {
        val app = getApplication<Application>()
        TagKeywordSource(TagStore.getTags(app), TagStore.getVideoTagsMap(app))
    }

    /**
     * 在后台刷新视频列表，并保证刷新指示至少显示 350 毫秒。
     */
    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true) }
        loadJob?.cancel()
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            try {

                refreshVideosInternal()
                delay(350.milliseconds)
            } finally {

                // 仅当本任务仍是最新加载任务时才清除刷新状态。
                if (loadJob === currentCoroutineContext()[Job]) {
                    _uiState.update { it.copy(isRefreshing = false) }
                }
            }
        }
    }

    /**
     * 在后台加载全部历史视频（首次进入及删除后刷新时调用）。
     */
    fun loadVideos() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                loadVideosInternal()
            } finally {
                // 仅最新加载任务收尾时清理刷新状态。
                if (loadJob === currentCoroutineContext()[Job]) {
                    _uiState.update { it.copy(isRefreshing = false) }
                }
            }
        }
    }

    private suspend fun loadVideosInternal() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        queryAndPublishVideos()
    }

    private suspend fun refreshVideosInternal() {
        _uiState.update { it.copy(error = null) }
        queryAndPublishVideos()
    }

    /**
     * 查询 MediaStore 历史视频并发布到 UI 状态，同时加载标签数据。
     */
    private suspend fun queryAndPublishVideos() {
        try {
            val items = mediaRepository.queryVideos()

            // 协程已取消（被新任务替换）时不发布结果，避免竞态。
            if (currentCoroutineContext().isActive) {

                val app = getApplication<Application>()
                val tags = TagStore.getTags(app)
                val tagMap = TagStore.getTagsForVideos(app, items.map { it.id })
                _uiState.update {
                    it.copy(
                        videos = items,
                        tags = tags,
                        tagMap = tagMap,
                        isLoading = false
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {

            // 协程已取消时静默返回，否则记录错误并提示。
            if (!currentCoroutineContext().isActive) return

            Log.e(TAG, "Failed to load history videos", e)
            val message = LanguagePrefs.string(
                getApplication(), R.string.history_load_failed
            )

            // 有旧内容时保留列表只弹 Toast；无内容时显示错误状态。
            val hadContent = _uiState.value.videos.isNotEmpty()
            _uiState.update {
                it.copy(
                    error = if (hadContent) null else message,
                    isLoading = false
                )
            }
            if (hadContent) {
                ToastCenter.show(getApplication(), message, Toast.LENGTH_SHORT)
            }
        }
        // 数据发布后重算显示列表，确保与当前排序/搜索一致。
        if (currentCoroutineContext().isActive) scheduleDisplayRecompute()
    }

    /**
     * 使用系统播放器打开视频，失败时提示用户。
     */
    fun play(context: Context, item: VideoItem) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(item.uri, "video/*")
            // 授权目标应用临时读取该 URI。
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // 授权须随 ClipData 提供，仅带 flag 会被部分接收方忽略。
            clipData = android.content.ClipData.newRawUri(null, item.uri)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            ToastCenter.show(
                context,
                LanguagePrefs.string(context, R.string.history_play_failed),
                Toast.LENGTH_SHORT
            )
        }
    }

    /**
     * 构建 Android 11+ 的系统删除确认请求。
     */
    fun buildDeleteRequest(items: List<VideoItem>): android.app.PendingIntent? =
        mediaRepository.buildDeleteRequest(items)

    /**
     * 旧版删除确认后的执行入口：在 ViewModel 作用域逐个删除并重载列表，配置变更不中断在途删除。
     */
    fun deleteLegacyItems(items: List<VideoItem>) {
        viewModelScope.launch(Dispatchers.IO) {
            // 用应用 Context：viewModelScope 可能长于 Activity 生命周期，不得持有界面 Context。
            val app = getApplication<Application>()
            val failed = mediaRepository.delete(items)
            // 同一提示文本按批提示一次，避免逐项失败刷屏。
            if (failed.isNotEmpty()) {
                ToastCenter.show(
                    app,
                    LanguagePrefs.string(app, R.string.history_delete_failed),
                    Toast.LENGTH_SHORT
                )
            }
            loadVideos()
        }
    }

    /**
     * 系统删除对话框确认后调用：系统 UI 允许逐项取消，仅清理确实已删除视频的标签，再重载列表。
     */
    fun onSystemDeleteConfirmed(pendingIds: Set<Long>) {
        if (pendingIds.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val deleted = pendingIds - mediaRepository.remainingVideoIds()
                if (deleted.isNotEmpty()) {
                    TagStore.removeVideoTags(getApplication(), deleted)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to diff tags after system delete", e)
            }
            loadVideos()
        }
    }

    /**
     * 重命名并在完成后重载列表；配置变更不会中断在途 IO（与删除路径一致）。
     *
     * 用应用 Context：viewModelScope 可能长于 Activity 生命周期，不得持有界面 Context。
     */
    fun renameAndReload(item: VideoItem, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            when (mediaRepository.rename(item, newName)) {
                RenameResult.SUCCESS -> loadVideos()
                RenameResult.UNCHANGED -> Unit
                RenameResult.FAILED -> ToastCenter.show(
                    getApplication(), R.string.history_rename_failed, Toast.LENGTH_SHORT
                )
            }
        }
    }

    /**
     * 通过系统分享面板分享视频（MP4），失败时提示用户。
     */
    fun share(context: Context, item: VideoItem) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, item.uri)
            // 授权须随 ClipData 提供，仅带 flag 会被部分接收方忽略。
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newUri(context.contentResolver, null, item.uri)
        }
        try {

            context.startActivity(
                Intent.createChooser(
                    intent,
                    LanguagePrefs.string(getApplication(), R.string.history_share_title)
                )
            )
        } catch (e: Exception) {
            ToastCenter.show(
                context,
                LanguagePrefs.string(context, R.string.history_share_failed),
                Toast.LENGTH_SHORT
            )
        }
    }
}
