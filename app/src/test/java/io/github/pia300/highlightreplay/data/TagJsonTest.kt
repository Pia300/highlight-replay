package io.github.pia300.highlightreplay.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 标签 JSON 解析：损坏记录必须被跳过而不是让整份数据失效。 */
class TagJsonTest {

    /** 正常记录逐条解析，顺序与字段保持。 */
    @Test
    fun parsesValidTagList() {
        val raw = """[{"id":"a","name":"One","color":255},{"id":"b","name":"Two","color":65535}]"""
        val tags = TagJson.parseTagRecords(raw)
        assertEquals(2, tags.size)
        assertEquals(VideoTag("a", "One", 255), tags[0])
        assertEquals(VideoTag("b", "Two", 65535), tags[1])
    }

    /** 单条损坏（缺 id / 缺 name / 非对象）只跳过该条，其余仍可用。 */
    @Test
    fun skipsCorruptRecordsButKeepsValidOnes() {
        val raw = """[
            {"id":"a","name":"One","color":1},
            {"name":"missing id"},
            {"id":"c"},
            42,
            {"id":"d","name":"Four","color":4}
        ]"""
        assertEquals(listOf("a", "d"), TagJson.parseTagRecords(raw).map { it.id })
    }

    /** 整体损坏、空值与 null 都返回空列表（默认标签由 getTags 注入，不在这里）。 */
    @Test
    fun returnsEmptyOnBrokenOrMissingJson() {
        assertTrue(TagJson.parseTagRecords("{not json").isEmpty())
        assertTrue(TagJson.parseTagRecords("[]").isEmpty())
        assertTrue(TagJson.parseTagRecords("").isEmpty())
        assertTrue(TagJson.parseTagRecords(null).isEmpty())
    }

    /** 视频-标签映射：正常条目解析，空 tagIds 保留为空列表，损坏条目跳过。 */
    @Test
    fun parsesVideoTagMap() {
        val raw = """[
            {"videoId":"1","tagIds":["a","b"]},
            {"videoId":"2","tagIds":[]},
            {"tagIds":["x"]},
            {"videoId":"4","tagIds":"not-an-array"}
        ]"""
        val map = TagJson.parseVideoTagRecords(raw)
        assertEquals(listOf("a", "b"), map["1"])
        assertEquals(emptyList<String>(), map["2"])
        assertEquals(2, map.size)
    }

    /** 映射整体损坏或缺失时返回空映射。 */
    @Test
    fun videoTagMapHandlesBrokenInput() {
        assertTrue(TagJson.parseVideoTagRecords("nope").isEmpty())
        assertTrue(TagJson.parseVideoTagRecords(null).isEmpty())
    }
}
