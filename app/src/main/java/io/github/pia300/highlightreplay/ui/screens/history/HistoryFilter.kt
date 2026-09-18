package io.github.pia300.highlightreplay.ui.screens.history

import io.github.pia300.highlightreplay.data.TagStore
import io.github.pia300.highlightreplay.data.VideoTag
import java.util.Locale

/** 关键词匹配所需的标签数据快照。 */
internal class TagKeywordSource(
    /** 全部标签。 */
    val allTags: List<VideoTag>,
    /** 视频 ID 字符串 → 该视频的标签 ID 列表。 */
    val videoTags: Map<String, List<String>>
)

/**
 * 按关键词过滤历史视频并排序。
 *
 * 关键词非空时，名称或任意标签包含关键词即视为匹配；[tagSource] 仅在需要标签匹配时调用一次。
 *
 * @param videos 待过滤的视频列表。
 * @param sort 排序方式。
 * @param query 用户输入的关键词，空白表示不过滤。
 * @param tagSource 标签数据来源，延迟到实际需要时调用。
 */
internal fun filterAndSortVideos(
    videos: List<VideoItem>,
    sort: HistorySort,
    query: String,
    tagSource: () -> TagKeywordSource
): List<VideoItem> {
    val filtered = if (query.isBlank()) videos
    else {
        val q = query.trim()

        // 统一小写匹配以忽略大小写；Locale.ROOT 保证结果稳定。
        val qLower = q.lowercase(Locale.ROOT)

        val source = tagSource()
        // 名称或任意标签包含关键词即视为匹配。
        videos.filter {

            it.displayName.lowercase(Locale.ROOT).contains(qLower) ||
                    TagStore.videoMatchesTagKeyword(
                        source.allTags,
                        source.videoTags[it.id.toString()] ?: emptyList(),
                        qLower
                    )
        }
    }
    return when (sort) {
        HistorySort.DATE_DESC -> filtered.sortedByDescending { it.dateAdded }
        HistorySort.DATE_ASC -> filtered.sortedBy { it.dateAdded }
        HistorySort.NAME_ASC -> filtered.sortedBy { it.displayName.lowercase(Locale.ROOT) }
        HistorySort.NAME_DESC -> filtered.sortedByDescending { it.displayName.lowercase(Locale.ROOT) }
        HistorySort.SIZE_DESC -> filtered.sortedByDescending { it.sizeBytes }
        HistorySort.SIZE_ASC -> filtered.sortedBy { it.sizeBytes }
    }
}
