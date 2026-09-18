package io.github.pia300.highlightreplay.data

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/** 标签与视频-标签映射的 JSON 编解码。 */
internal object TagJson {

    private const val TAG = "TagStore"

    /**
     * 解析标签列表 JSON：单条损坏只跳过该条，整体损坏返回空列表（默认标签由 TagStore 注入）。
     * 单元测试直接覆盖脏数据路径。
     */
    fun parseTagRecords(raw: String?): List<VideoTag> {
        if (raw == null) return emptyList()
        val result = mutableListOf<VideoTag>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {

                // 逐条解析：单条损坏只跳过该条，不影响其余标签。
                try {
                    val obj = arr.getJSONObject(i)
                    result.add(
                        VideoTag(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            color = obj.getLong("color")
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Skipping corrupt tag record #$i: ${e.message}")
                }
            }
        // 整体解析失败时回退为空列表；日志不带原始数据（含用户标签名）。
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse tag list", e)
        }
        return result
    }

    /**
     * 解析 videoId → tagIds 映射 JSON：单条损坏只跳过该条，整体损坏返回空映射。
     * 单元测试直接覆盖脏数据路径。
     */
    fun parseVideoTagRecords(raw: String?): Map<String, List<String>> {
        if (raw == null) return emptyMap()
        val map = mutableMapOf<String, List<String>>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {

                // 逐条解析：单条损坏只跳过该条，不影响其余映射。
                try {
                    val obj = arr.getJSONObject(i)
                    val ids = mutableListOf<String>()
                    val idArr = obj.getJSONArray("tagIds")
                    for (j in 0 until idArr.length()) ids.add(idArr.getString(j))
                    map[obj.getString("videoId")] = ids
                } catch (e: Exception) {
                    Log.e(TAG, "Skipping corrupt video_tags record #$i: ${e.message}")
                }
            }
        // 整体解析失败时回退为空映射。
        } catch (e: Exception) {

            Log.e(TAG, "Failed to parse video_tags map", e)
        }
        return map
    }

    /** 序列化标签列表为 JSON；[excludeId] 指定的标签不落库。 */
    fun serializeTagRecords(tags: List<VideoTag>, excludeId: String): String {
        val arr = JSONArray()
        tags.filter { it.id != excludeId }.forEach { t ->
            arr.put(
                JSONObject()
                    .put("id", t.id)
                    .put("name", t.name)
                    .put("color", t.color)
            )
        }
        return arr.toString()
    }

    /** 序列化视频-标签映射为 JSON；空标签列表的条目被省略。 */
    fun serializeVideoTagRecords(videoTags: Map<String, List<String>>): String {
        val arr = JSONArray()
        videoTags.forEach { (videoId, ids) ->
            if (ids.isNotEmpty()) {
                arr.put(JSONObject().put("videoId", videoId).put("tagIds", JSONArray(ids)))
            }
        }
        return arr.toString()
    }
}
