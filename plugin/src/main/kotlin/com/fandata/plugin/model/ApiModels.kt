package com.fandata.plugin.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * ID 编码：bookUrl|sourceUrl|type
 * 章节ID：chapterUrl|title|sourceUrl|type|tocUrl
 */
object IdCodec {
    fun encodeBookId(bookUrl: String, sourceUrl: String, type: String) = "$bookUrl|$sourceUrl|$type"
    fun decodeBookId(encoded: String): Triple<String, String, String> {
        val p = encoded.split("|", limit = 3)
        return Triple(p.getOrElse(0){""}, p.getOrElse(1){""}, p.getOrElse(2){"default"})
    }
    fun encodeChapterId(chapterUrl: String, title: String, sourceUrl: String, type: String, tocUrl: String = "") = "$chapterUrl|$title|$sourceUrl|$type|$tocUrl"
    fun decodeChapterId(encoded: String): ChapterIdParts {
        val p = encoded.split("|", limit = 5)
        return ChapterIdParts(p.getOrElse(0){""}, p.getOrElse(1){""}, p.getOrElse(2){""}, p.getOrElse(3){"default"}, p.getOrElse(4){""})
    }
}
data class ChapterIdParts(val itemId: String, val title: String, val sourceUrl: String, val type: String, val tocUrl: String)