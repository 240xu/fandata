package com.fandata.plugin.utils

import android.net.Uri
import io.legado.engine.entity.*
import io.nightfish.lightnovelreader.api.book.*
import io.nightfish.lightnovelreader.api.content.builder.ContentBuilder
import io.nightfish.lightnovelreader.api.content.component.SimpleTextComponentData
import java.time.LocalDateTime

/**
 * 内容转换工具 - 将引擎实体转为 LNR API 数据模型
 */
object ContentUtils {

    fun searchBookToBookInfo(sb: SearchBook, source: BookSource): BookInformation {
        val bookId = com.fandata.plugin.model.IdCodec.encodeBookId(sb.bookUrl, source.bookSourceUrl, "default")
        return MutableBookInformation(
            id = bookId,
            title = sb.name,
            subtitle = source.bookSourceName,
            coverUrl = if (!sb.coverUrl.isNullOrBlank()) Uri.parse(sb.coverUrl) else Uri.EMPTY,
            author = sb.author,
            description = sb.intro ?: "",
            tags = if (!sb.kind.isNullOrBlank()) sb.kind!!.split(",").map { it.trim() } else emptyList(),
            publishingHouse = "",
            wordCount = WordCount(0),
            lastUpdated = LocalDateTime.MIN,
            isComplete = false
        )
    }

    fun bookToBookInfo(book: Book, source: BookSource): BookInformation {
        val bookId = com.fandata.plugin.model.IdCodec.encodeBookId(book.bookUrl, source.bookSourceUrl, "default")
        return MutableBookInformation(
            id = bookId,
            title = book.name,
            subtitle = source.bookSourceName,
            coverUrl = if (!book.coverUrl.isNullOrBlank()) Uri.parse(book.coverUrl) else Uri.EMPTY,
            author = book.author,
            description = book.intro ?: "",
            tags = if (!book.kind.isNullOrBlank()) book.kind!!.split(",").map { it.trim() } else emptyList(),
            publishingHouse = "",
            wordCount = WordCount(book.wordCount?.replace(Regex("[^0-9]"), "")?.toIntOrNull() ?: 0),
            lastUpdated = LocalDateTime.MIN,
            isComplete = false
        )
    }

    fun chaptersToVolumes(bookId: String, chapters: List<BookChapter>, sourceUrl: String): BookVolumes {
        if (chapters.isEmpty()) return BookVolumes.empty(bookId)
        val chapterInfos = chapters.map { ch ->
            val cid = com.fandata.plugin.model.IdCodec.encodeChapterId(ch.url, ch.title, sourceUrl, "default", ch.tocUrl)
            ChapterInformation(id = cid, title = ch.title)
        }
        return BookVolumes(bookId, listOf(Volume("${bookId}_v0", "", chapterInfos)))
    }

    fun toChapterContent(chapterId: String, title: String, raw: String): ChapterContent {
        if (raw.isBlank()) return ChapterContent.empty(chapterId)
        val builder = ContentBuilder()
        raw.replace("\r\n", "\n").replace("\r", "\n").split("\n")
            .filter { it.isNotBlank() }
            .forEach {
                builder.component(SimpleTextComponentData(it.trim()))
            }
        return MutableChapterContent(id = chapterId, title = title, content = builder.build())
    }
}