package io.github.pia300.highlightreplay.ui.screens.history

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.pia300.highlightreplay.data.TagCoverage
import io.github.pia300.highlightreplay.data.TagStore
import io.github.pia300.highlightreplay.data.thumbnailCache
import kotlinx.coroutines.launch

/**
 * 历史记录主界面：展示录屏视频列表，支持搜索、排序、下拉刷新、多选删除、重命名与标签管理。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = viewModel(),

    onSelectionModeChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val videos = uiState.videos
    val displayVideos = uiState.displayVideos
    val isLoading = uiState.isLoading
    val error = uiState.error
    val sort = uiState.sort
    val query = uiState.query
    val isRefreshing = uiState.isRefreshing

    val allTags = uiState.tags
    val tagMap = uiState.tagMap

    // 对话框状态一律以 id 保存：配置变更后从最新列表重新解析，避免持有过期对象。
    var tagEditTargetId by rememberSaveable { mutableStateOf<Long?>(null) }
    val tagEditTarget = tagEditTargetId?.let { id -> uiState.videos.firstOrNull { it.id == id } }

    var tagSelectedIds by rememberSaveable(stateSaver = stringSetSaver) {
        mutableStateOf(emptySet())
    }

    // 回到前台（ON_RESUME）时重新加载列表；组合建立时生命周期若已在 RESUMED，
    // addObserver 会同步补发 ON_RESUME，冷启动/旋转重建/页签重入因此各只触发一次加载。
    val lifecycleOwner = LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadVideos()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 选择模式与已选 ID 用 rememberSaveable 保存，配置变更后仍保留。
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedIds by rememberSaveable(stateSaver = longSetSaver) { mutableStateOf(emptySet()) }

    val tagScope = rememberCoroutineScope()

    // 只保留当前显示列表中的选中项，被过滤掉的选中项不在可操作范围内。
    val validSelectedIds = remember(selectedIds, displayVideos) {
        val valid = displayVideos.mapTo(HashSet()) { it.id }
        selectedIds intersect valid
    }

    fun toggleSelect(id: Long) {
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
        if (selectedIds.isEmpty()) selectionMode = false
    }

    LaunchedEffect(selectionMode) {
        onSelectionModeChange(selectionMode)
    }

    // 提交给系统删除请求的 id 快照：RESULT_OK 后与现存视频差集，仅清理确实已删视频的标签。
    // 快照经 rememberSaveable 保存，删除对话框打开期间发生配置变更时 RESULT_OK 仍会重投。
    var pendingSystemDeleteIds by rememberSaveable(stateSaver = longSetSaver) {
        mutableStateOf(emptySet())
    }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->

        if (result.resultCode == android.app.Activity.RESULT_OK) {

            thumbnailCache.evictAll()

            if (pendingSystemDeleteIds.isNotEmpty()) {
                viewModel.onSystemDeleteConfirmed(pendingSystemDeleteIds)
            }
            pendingSystemDeleteIds = emptySet()

            selectedIds = emptySet()
            selectionMode = false
        } else {
            pendingSystemDeleteIds = emptySet()
        }
    }

    // 旧版删除路径（Android 10 及以下）的待删除视频 id，非空时弹应用内确认框。
    var legacyDeleteTargetIds by rememberSaveable(stateSaver = longSetSaver) {
        mutableStateOf(emptySet())
    }
    val legacyDeleteTargets = if (legacyDeleteTargetIds.isEmpty()) null
    else uiState.videos.filter { it.id in legacyDeleteTargetIds }

    // 用 id 而非对象保存：配置变更后从最新列表重新解析，对话框已输入文本也不会丢失。
    var renameTargetId by rememberSaveable { mutableStateOf<Long?>(null) }
    val renameTarget = renameTargetId?.let { id -> uiState.videos.firstOrNull { it.id == id } }

    var tagBulkOpen by rememberSaveable { mutableStateOf(false) }

    var tagBulkSelectedIds by rememberSaveable(stateSaver = stringSetSaver) {
        mutableStateOf(emptySet())
    }

    var tagBulkCoverage by rememberSaveable(stateSaver = tagCoverageMapSaver) {
        mutableStateOf(emptyMap())
    }

    // 覆盖状态需全量解析标签 JSON，加载完成前禁用标签块：否则会沿用上一批的勾选态，
    // 且此窗口内点击会按「全加或全删」写入与用户意图相反的结果。
    var tagBulkLoading by rememberSaveable { mutableStateOf(false) }

    // 批量标签操作的目标 id 列表，随显示列表与有效选中项变化。
    val tagBulkTargetIds = remember(displayVideos, validSelectedIds) {
        displayVideos.filter { it.id in validSelectedIds }.map { it.id }
    }

    // 选择模式下按返回键退出选择模式而非退出页面。
    BackHandler(enabled = selectionMode) {
        selectedIds = emptySet()
        selectionMode = false
    }

    val pullToRefreshState = rememberPullToRefreshState()

    val listState = rememberLazyListState()

    // 排序切换时的列表透明度，用于淡出/淡入动画。
    val listAlpha = remember { Animatable(1f) }
    val sortFadeScope = rememberCoroutineScope()
    val sortFadeJobHolder = remember { SortFadeJobHolder() }

    // 查询条件变化（含首次组合）即回到顶部；列表加载统一由 ON_RESUME 观察者触发。
    LaunchedEffect(query) {
        listState.scrollToItem(0)
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (selectionMode) {

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                SelectionToolbar(
                    selectedCount = validSelectedIds.size,
                    allSelected = displayVideos.isNotEmpty() && validSelectedIds.size == displayVideos.size,
                    onToggleAll = {

                        selectedIds = if (validSelectedIds.size == displayVideos.size) emptySet()
                        else displayVideos.map { it.id }.toSet()
                    },
                    onDeleteSelected = {
                        val toDelete = displayVideos.filter { it.id in validSelectedIds }
                        val pending = viewModel.buildDeleteRequest(toDelete)
                        if (pending != null) {
                            pendingSystemDeleteIds = toDelete.mapTo(HashSet()) { it.id }
                            deleteLauncher.launch(
                                androidx.activity.result.IntentSenderRequest.Builder(
                                    pending.intentSender
                                ).build()
                            )
                        } else {
                            // 旧设备无系统删除确认框，改为应用内确认对话框。
                            legacyDeleteTargetIds = toDelete.mapTo(HashSet()) { it.id }
                        }
                    },
                    onTagSelected = {
                        tagBulkOpen = true
                        // 先清空上一批的覆盖状态并置加载中，避免沿用旧勾选。
                        tagBulkCoverage = emptyMap()
                        tagBulkSelectedIds = emptySet()
                        tagBulkLoading = true

                        val targetIds =
                            displayVideos.filter { it.id in validSelectedIds }.map { it.id }
                        // 覆盖状态需全量解析标签 JSON：放 IO 线程算完再回主线程更新选中态。
                        tagScope.launch {
                            val coverage = viewModel.tagCoverages(targetIds, allTags.map { it.id })
                            tagBulkCoverage = coverage
                            tagBulkSelectedIds = allTags.filter {
                                coverage[it.id] == TagCoverage.ALL
                            }.map { it.id }.toSet()
                            tagBulkLoading = false
                        }
                    },
                    onExit = {
                        selectedIds = emptySet()
                        selectionMode = false
                    }
                )
            }
        } else {

            HistoryToolbar(
                query = query,
                onQueryChange = viewModel::setQuery,
                sort = sort,
                onSortChange = { newSort ->
                    // 排序实际变化时才播放淡出/淡入；等排序状态发布后再滚动到顶部。
                    if (newSort != sort) {
                        launchSortFade(
                            scope = sortFadeScope,
                            jobHolder = sortFadeJobHolder,
                            listAlpha = listAlpha,
                            listState = listState,
                            currentSort = sort,
                            readSort = { uiState.sort }
                        )
                    }
                    viewModel.setSort(newSort)
                }
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                isLoading && videos.isEmpty() -> {
                    HistoryLoadingState(modifier = Modifier.align(Alignment.Center))
                }

                error != null && videos.isEmpty() -> {
                    HistoryErrorState(
                        message = error,
                        onRetry = { viewModel.loadVideos() },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                displayVideos.isEmpty() -> {
                    HistoryEmptyState(
                        query = query,
                        onRefresh = { viewModel.loadVideos() },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                else -> {

                    // 下拉刷新容器，透明度跟随排序动画。
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = { viewModel.refresh() },
                        state = pullToRefreshState,
                        modifier = Modifier
                            .fillMaxSize()

                            .graphicsLayer { alpha = listAlpha.value },
                        indicator = {
                            PullToRefreshDefaults.Indicator(
                                state = pullToRefreshState,
                                isRefreshing = isRefreshing,
                                modifier = Modifier.align(Alignment.TopCenter),
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            // 以视频 ID 为稳定 key，排序重排后保持每行状态。
                            items(displayVideos, key = { it.id }) { item ->
                                VideoRow(
                                    item = item,
                                    tags = tagMap[item.id] ?: emptyList(),
                                    selectionMode = selectionMode,
                                    selected = item.id in selectedIds,

                                    onLongPress = {
                                        selectionMode = true
                                        toggleSelect(item.id)
                                    },
                                    onToggleSelect = { toggleSelect(item.id) },
                                    onPlay = { viewModel.play(context, item) },
                                    onRename = { renameTargetId = item.id },
                                    onShare = { viewModel.share(context, item) },
                                    onEditTags = {
                                        tagEditTargetId = item.id

                                        tagSelectedIds =
                                            tagMap[item.id]?.map { it.id }?.toSet() ?: emptySet()
                                    },
                                    onDelete = {
                                        val pending =
                                            viewModel.buildDeleteRequest(listOf(item))
                                        if (pending != null) {
                                            pendingSystemDeleteIds = setOf(item.id)
                                            deleteLauncher.launch(
                                                androidx.activity.result.IntentSenderRequest.Builder(
                                                    pending.intentSender
                                                ).build()
                                            )
                                        } else {
                                            legacyDeleteTargetIds = setOf(item.id)
                                        }
                                    }
                                )
                                HorizontalDivider(

                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    HistoryDialogHosts(
        legacyDeleteTargets = legacyDeleteTargets,
        onLegacyDeleteConfirm = { targets ->
            // 立即收起对话框防重复确认；删除在 ViewModel 作用域执行，旋转等配置变更不中断。
            legacyDeleteTargetIds = emptySet()
            viewModel.deleteLegacyItems(targets)
            thumbnailCache.evictAll()
            selectedIds = emptySet()
            selectionMode = false
        },
        onLegacyDeleteDismiss = { legacyDeleteTargetIds = emptySet() },
        renameTarget = renameTarget,
        onRenameConfirm = { target, newName ->
            // 走 ViewModel 的 viewModelScope：配置变更不会取消在途重命名（与删除路径一致）。
            viewModel.renameAndReload(target, newName)
            renameTargetId = null
        },
        onRenameDismiss = { renameTargetId = null },
        tagBulkOpen = tagBulkOpen,
        tagBulkTargetIds = tagBulkTargetIds,
        allTags = allTags,
        tagBulkSelectedIds = tagBulkSelectedIds,
        tagBulkCoverage = tagBulkCoverage,
        // 切换标签即写入存储（IO 线程），并同步本地选中状态与覆盖信息；写盘失败由 mutateTags 提示。
        onTagBulkToggleTag = { tagId ->
            viewModel.mutateTags { app -> TagStore.applyTagToVideos(app, tagBulkTargetIds, tagId) }
            tagBulkSelectedIds = if (tagId in tagBulkSelectedIds) tagBulkSelectedIds - tagId
            else tagBulkSelectedIds + tagId
            tagBulkCoverage = tagBulkCoverage + (tagId to
                    (if (tagId in tagBulkSelectedIds) TagCoverage.ALL else TagCoverage.NONE))
        },
        onTagBulkDeleteTag = { tagId ->
            viewModel.mutateTags { app -> TagStore.deleteTag(app, tagId) }
            tagBulkSelectedIds = tagBulkSelectedIds - tagId
            tagBulkCoverage = tagBulkCoverage - tagId
        },
        // 新建标签：重名判断用已加载的标签列表同步完成（纯内存比较，供输入框即时提示），
        // 实际写入走 IO 线程——TagStore.saveTag 会全量解析并重新序列化 JSON；写盘失败由 mutateTags 提示。
        onTagBulkCreateTag = { name, color ->
            val normalized = name.trim()
            val duplicated = allTags.any {
                it.name.trim().equals(normalized, ignoreCase = true)
            }
            if (normalized.isEmpty() || duplicated) {
                false
            } else {
                viewModel.mutateTags { app ->
                    TagStore.saveTag(
                        app,
                        io.github.pia300.highlightreplay.data.VideoTag(
                            id = TagStore.newTagId(),
                            name = normalized,
                            color = color
                        )
                    )
                }
                true
            }
        },
        onTagBulkDismiss = { tagBulkOpen = false },
        tagBulkChipsEnabled = !tagBulkLoading,
        tagEditTarget = tagEditTarget,
        tagSelectedIds = tagSelectedIds,
        onTagEditToggleTag = { target, tagId ->
            viewModel.mutateTags { app -> TagStore.toggleTag(app, target.id, tagId) }
            tagSelectedIds = if (tagId in tagSelectedIds) tagSelectedIds - tagId
            else tagSelectedIds + tagId
        },
        onTagEditDeleteTag = { tagId ->
            viewModel.mutateTags { app -> TagStore.deleteTag(app, tagId) }
            tagSelectedIds = tagSelectedIds - tagId
        },
        // 重名判断用已加载的标签列表同步完成（纯内存比较），实际写入下放 IO 线程：
        // TagStore.saveTag 会全量解析并重新序列化标签 JSON，直接调用会阻塞主线程；写盘失败由 mutateTags 提示。
        onTagEditCreateTag = { name, color ->
            val normalized = name.trim()
            val duplicated = allTags.any {
                it.name.trim().equals(normalized, ignoreCase = true)
            }
            if (normalized.isEmpty() || duplicated) {
                false
            } else {
                viewModel.mutateTags { app ->
                    TagStore.saveTag(
                        app,
                        io.github.pia300.highlightreplay.data.VideoTag(
                            id = TagStore.newTagId(),
                            name = normalized,
                            color = color
                        )
                    )
                }
                true
            }
        },
        onTagEditDismiss = { tagEditTargetId = null }
    )
}

/**
 * 将 Set<Long> 保存为 ArrayList<Long> 的自定义 Saver。
 */
private val longSetSaver = Saver<Set<Long>, ArrayList<Long>>(
    save = { ArrayList(it) },
    restore = { it.toSet() }
)

/** 将 Set<String> 保存为 ArrayList<String> 的自定义 Saver。 */
private val stringSetSaver = Saver<Set<String>, ArrayList<String>>(
    save = { ArrayList(it) },
    restore = { it.toSet() }
)

/** 将标签 id → 覆盖状态的映射保存为「id + NUL 分隔符 + 状态名」的字符串列表。 */
private val tagCoverageMapSaver = Saver<Map<String, TagCoverage>, ArrayList<String>>(
    save = { map -> ArrayList(map.map { (id, coverage) -> "$id\u0000${coverage.name}" }) },
    restore = { saved ->
        saved.mapNotNull { entry ->
            val separator = entry.lastIndexOf('\u0000')
            if (separator <= 0) return@mapNotNull null
            val coverage = TagCoverage.entries.firstOrNull {
                it.name == entry.substring(separator + 1)
            } ?: return@mapNotNull null
            entry.substring(0, separator) to coverage
        }.toMap()
    }
)
