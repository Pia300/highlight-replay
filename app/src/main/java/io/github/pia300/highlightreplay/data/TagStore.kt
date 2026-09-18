package io.github.pia300.highlightreplay.data

import android.content.Context
import android.util.Log
import io.github.pia300.highlightreplay.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

/** 用户自定义标签的数据模型：唯一 ID、显示名称与颜色。 */
data class VideoTag(
    val id: String,
    val name: String,
    val color: Long
)

/** 标签在所选视频中的覆盖状态：无、部分或全部。 */
enum class TagCoverage { NONE, PARTIAL, ALL }

/** 以 SharedPreferences + JSON 持久化标签与视频-标签映射，通过变更计数器通知 UI 刷新。 */
object TagStore {

    private const val TAG = "TagStore"
    private const val PREFS_NAME = "tag_prefs"
    private const val KEY_TAGS = "tags"
    private const val KEY_VIDEO_TAGS = "video_tags"

    /** 内置默认标签的固定 ID；显示名取本地化资源 tag_default_name。 */
    const val DEFAULT_TAG_ID = "default_favorite"
    /** 默认标签的 ARGB 颜色（粉 0xFFE91E63）。 */
    const val DEFAULT_TAG_COLOR = 0xFFE91E63
    /** 默认标签是否已被用户删除的标记键；置位后 getTags 不再注入默认标签。 */
    private const val KEY_DEFAULT_TAG_DELETED = "default_tag_deleted"

    fun newTagId(): String = "tag_${java.util.UUID.randomUUID()}"

    /** 变更计数器：每次增删改递增，changeCount 为其只读对外视图。 */
    private val _changeCount = MutableStateFlow(0)
    val changeCount: StateFlow<Int> = _changeCount.asStateFlow()

    private fun notifyChanged() {

        _changeCount.update { it + 1 }
    }

    /**
     * 同步提交一次偏好写入，返回是否成功落盘。
     *
     * 用 [android.content.SharedPreferences.Editor.commit] 而非 `apply`：`apply` 的磁盘写是异步的，
     * 失败只记日志、不抛异常也不返回结果，调用方无从得知标签是否真的保存。
     * KTX 的 `SharedPreferences.edit` 两个变体均返回 Unit，取不到落盘结果，故此处不用。
     */
    @Suppress("UseKtx")
    private fun commitBlock(
        prefs: android.content.SharedPreferences,
        block: (android.content.SharedPreferences.Editor) -> Unit
    ): Boolean {
        val editor = prefs.edit()
        block(editor)
        return editor.commit()
    }

    /** 缓存解析默认标签名时使用的语言，用于判断缓存是否过期。 */
    @Volatile
    private var cachedDefaultNameLang: String? = null
    /** 缓存解析出的默认标签名。 */
    @Volatile
    private var cachedDefaultName: String? = null

    /** 解析默认标签名：语言未变时返回缓存，否则重载并更新缓存。 */
    private fun defaultTagName(context: Context): String {
        val lang = LanguagePrefs.current(context)

        val effectiveLang = if (lang == LanguagePrefs.LANG_SYSTEM) {
            android.content.res.Resources.getSystem().configuration.locales.toLanguageTags()
        } else lang
        cachedDefaultName?.let {
            if (cachedDefaultNameLang == effectiveLang) return it
        }
        return LanguagePrefs.string(context, R.string.tag_default_name).also {
            cachedDefaultName = it
            cachedDefaultNameLang = effectiveLang
        }
    }

    /** 读取全部标签：默认标签在用户删除前注入头部；损坏记录跳过并记日志。 */
    @Synchronized
    fun getTags(context: Context): List<VideoTag> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getStringSafe(KEY_TAGS)

        val result = mutableListOf<VideoTag>()
        // 默认标签可被删除：删除后（墓碑置位）不再注入。
        if (!prefs.getBooleanSafe(KEY_DEFAULT_TAG_DELETED, false)) {
            result.add(
                VideoTag(
                    DEFAULT_TAG_ID,
                    defaultTagName(context),
                    DEFAULT_TAG_COLOR
                )
            )
        }
        if (raw != null) {
            // 通用同 id 去重：seen 预置默认标签 id，存储中残留的重复项（含默认标签）一律跳过。
            val seenIds = HashSet<String>().apply { add(DEFAULT_TAG_ID) }
            for (tag in TagJson.parseTagRecords(raw)) {
                if (!seenIds.add(tag.id)) {
                    Log.w(TAG, "Skipping duplicate tag record (id=${tag.id})")
                    continue
                }
                result.add(tag)
            }
        }
        return result.toList()
    }

    /** 保存或更新标签；默认标签不可修改，重复名称被拒绝。返回是否成功落盘。 */
    @Synchronized
    fun saveTag(context: Context, tag: VideoTag): Boolean {
        if (tag.id == DEFAULT_TAG_ID) return false
        // 名称规范化后参与存储与唯一性比较：去首尾空白、按小写折叠忽略大小写差异。
        val normalizedName = tag.name.trim()
        val normalizedTag = tag.copy(name = normalizedName)
        if (normalizedName.isEmpty()) return false
        val tags = getTags(context).filter { it.id != tag.id }
        // 标签名称必须唯一。
        if (tags.any { it.name.trim().equals(normalizedName, ignoreCase = true) }) {
            return false
        }
        val newList = tags + normalizedTag

        // 写前剔除默认标签，保证其永不落库（入口已拒 DEFAULT_TAG_ID，此处仅过滤内存注入项）。
        val json = TagJson.serializeTagRecords(newList, DEFAULT_TAG_ID)
        val stored = commitBlock(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)) {
            it.putString(KEY_TAGS, json)
        }
        if (stored) notifyChanged()
        return stored
    }

    /** 删除标签，并在同一事务中将其从所有视频的标签列表中移除；默认标签同样可删（置墓碑后不再注入）。返回是否成功落盘。 */
    @Synchronized
    fun deleteTag(context: Context, tagId: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val tags = getTags(context).filter { it.id != tagId }
        // 同 saveTag：写前剔除内存注入的默认标签，保证其永不落库。
        val tagsJson = TagJson.serializeTagRecords(tags, DEFAULT_TAG_ID)

        // 同步清理视频-标签映射，避免残留指向已删除标签的引用。
        val videoTags = getVideoTags(context)
        // 迭代中仅改写既有条目的值（不增删 key），避免 HashMap 结构性修改抛 ConcurrentModificationException。
        videoTags.forEach { (videoId, ids) ->
            videoTags[videoId] = ids.filter { it != tagId }
        }
        val vtJson = TagJson.serializeVideoTagRecords(videoTags)
        // 标签列表、关联映射与默认标签墓碑在同一事务中提交，保证一致性。
        val stored = commitBlock(prefs) { editor ->
            editor.putString(KEY_TAGS, tagsJson)
            editor.putString(KEY_VIDEO_TAGS, vtJson)
            if (tagId == DEFAULT_TAG_ID) editor.putBoolean(KEY_DEFAULT_TAG_DELETED, true)
        }
        if (stored) notifyChanged()
        return stored
    }

    /** 批量查询多个视频各自关联的标签列表。 */
    @Synchronized
    fun getTagsForVideos(context: Context, videoIds: Collection<Long>): Map<Long, List<VideoTag>> {
        if (videoIds.isEmpty()) return emptyMap()
        val tagsById = getTags(context).associateBy { it.id }
        val videoTags = getVideoTags(context)
        return videoIds.associateWith { id ->
            (videoTags[id.toString()] ?: emptyList()).mapNotNull { tagsById[it] }
        }
    }

    /** 切换单个视频上某标签的启用状态（有则移除，无则添加）。返回是否成功落盘。 */
    @Synchronized
    fun toggleTag(context: Context, videoId: Long, tagId: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val videoTags = getVideoTags(context)
        val current = videoTags[videoId.toString()] ?: emptyList()
        val new = if (tagId in current) current - tagId else current + tagId
        videoTags[videoId.toString()] = new
        val stored = saveVideoTags(prefs, videoTags)
        if (stored) notifyChanged()
        return stored
    }

    /** 批量应用或移除同一标签：任一视频未带该标签则全部添加，否则全部移除。返回是否成功落盘。 */
    @Synchronized
    fun applyTagToVideos(context: Context, videoIds: List<Long>, tagId: String): Boolean {
        if (videoIds.isEmpty()) return true
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val videoTags = getVideoTags(context)

        val add = videoIds.any { tagId !in (videoTags[it.toString()] ?: emptyList()) }
        videoIds.forEach { id ->
            val current = videoTags[id.toString()] ?: emptyList()
            videoTags[id.toString()] =
                if (add) if (tagId in current) current else current + tagId
                else current - tagId
        }
        val stored = saveVideoTags(prefs, videoTags)
        if (stored) notifyChanged()
        return stored
    }

    /** 计算某标签在给定视频集合中的覆盖状态（无/部分/全部）。 */
    private fun tagCoverage(
        videoTags: Map<String, List<String>>,
        videoIds: List<Long>,
        tagId: String
    ): TagCoverage {
        // 空选择视为无覆盖。
        if (videoIds.isEmpty()) return TagCoverage.NONE
        var onCount = 0
        videoIds.forEach { id ->
            if (tagId in (videoTags[id.toString()] ?: emptyList())) onCount++
        }
        return when (onCount) {
            0 -> TagCoverage.NONE
            videoIds.size -> TagCoverage.ALL
            else -> TagCoverage.PARTIAL
        }
    }

    /** 批量计算多个标签在所选视频中的覆盖状态。 */
    @Synchronized
    fun tagCoverages(
        context: Context,
        videoIds: List<Long>,
        tagIds: List<String>
    ): Map<String, TagCoverage> {
        if (videoIds.isEmpty()) return tagIds.associateWith { TagCoverage.NONE }
        val videoTags = getVideoTags(context)
        return tagIds.associateWith { tagId -> tagCoverage(videoTags, videoIds, tagId) }
    }

    /** 命中关键词的条件：任一选中标签的名称（小写）包含关键词。 */
    fun videoMatchesTagKeyword(
        tags: List<VideoTag>,
        tagIds: List<String>,
        keywordLower: String
    ): Boolean {
        if (tagIds.isEmpty()) return false

        return tags.any {
            it.id in tagIds && it.name.lowercase(Locale.ROOT).contains(keywordLower)
        }
    }

    /** 视频-标签映射的不可变只读快照。 */
    fun getVideoTagsMap(context: Context): Map<String, List<String>> = getVideoTags(context).toMap()

    /** 清理已删除视频的标签映射，避免残留条目。返回是否成功落盘。 */
    @Synchronized
    fun removeVideoTags(context: Context, videoIds: Collection<Long>): Boolean {
        if (videoIds.isEmpty()) return true
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val videoTags = getVideoTags(context)
        videoIds.forEach { videoTags.remove(it.toString()) }
        val stored = saveVideoTags(prefs, videoTags)
        if (stored) notifyChanged()
        return stored
    }

    /** 读取并解析 videoId -> tagIds 映射为可变 Map，供后续修改。 */
    @Synchronized
    private fun getVideoTags(context: Context): MutableMap<String, List<String>> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return TagJson.parseVideoTagRecords(prefs.getStringSafe(KEY_VIDEO_TAGS)).toMutableMap()
    }

    /** 序列化视频-标签映射为 JSON 写入偏好。返回是否成功落盘。 */
    private fun saveVideoTags(
        prefs: android.content.SharedPreferences,
        videoTags: Map<String, List<String>>
    ): Boolean = commitBlock(prefs) {
        it.putString(KEY_VIDEO_TAGS, TagJson.serializeVideoTagRecords(videoTags))
    }
}
