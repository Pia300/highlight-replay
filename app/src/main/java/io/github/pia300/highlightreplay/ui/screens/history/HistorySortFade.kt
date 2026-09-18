package io.github.pia300.highlightreplay.ui.screens.history

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

/**
 * 排序动画协程容器：用普通对象引用替代 State，写 Job 不触发重组。
 */
internal class SortFadeJobHolder {
    var job: Job? = null
}

/**
 * 播放排序切换的淡出/淡入：先取消上一次动画，淡出后等待 [readSort] 发布出不同于 [currentSort]
 * 的值并回到列表顶部，协程未被取消时再淡入。
 */
internal fun launchSortFade(
    scope: CoroutineScope,
    jobHolder: SortFadeJobHolder,
    listAlpha: Animatable<Float, AnimationVector1D>,
    listState: LazyListState,
    currentSort: HistorySort,
    readSort: () -> HistorySort
) {
    jobHolder.job?.cancel()
    jobHolder.job = scope.launch {
        try {
            listAlpha.animateTo(0f, tween(120))

            // 等新排序状态发布。不能等 displayVideos 引用变化：新排序结果结构
            // 相同时（单条视频、排序键相同）StateFlow 不发射，会白等超时。
            withTimeoutOrNull(600.milliseconds) {
                snapshotFlow(readSort).first { it != currentSort }
            }
            listState.scrollToItem(0)
        } finally {

            // 仅当协程未被取消时才淡入。
            if (isActive) {
                listAlpha.animateTo(1f, tween(200))
            }
        }
    }
}
