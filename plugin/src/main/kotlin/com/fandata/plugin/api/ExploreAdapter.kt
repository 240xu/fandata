package com.fandata.plugin.api

import android.net.Uri
import android.util.Log
import com.fandata.plugin.model.IdCodec
import io.legado.engine.entity.BookSource
import io.legado.engine.entity.SearchBook
import io.legado.engine.webbook.WebBook
import io.nightfish.lightnovelreader.api.explore.*

/**
 * ExploreAdapter - 将 Legado ruleExplore 转换为 LNR 探索页数据模型
 */
object ExploreAdapter {
    private const val TAG = "ExploreAdapter"

    fun loadExploreData(source: BookSource): List<ExploreBooksRow> {
        val exploreUrl = source.exploreUrl
        val isJsOnly = exploreUrl.isNullOrBlank() ||
            exploreUrl.trim().startsWith("<js>") ||
            exploreUrl.trim().startsWith("@")

        return if (isJsOnly) {
            loadFromJs(source, exploreUrl)
        } else {
            loadFromUrl(source, exploreUrl!!)
        }
    }

    private fun loadFromJs(source: BookSource, exploreUrl: String?): List<ExploreBooksRow> {
        try {
            val books = WebBook.exploreBook(source, exploreUrl ?: "")
            if (books.isNotEmpty()) {
                return listOf(buildRow(source.bookSourceName, books, source))
            }
        } catch (e: Exception) {
            Log.w(TAG, "JS探索规则执行失败: ${e.message}")
        }
        return fallbackToSearch(source)
    }

    private fun loadFromUrl(source: BookSource, url: String): List<ExploreBooksRow> {
        return try {
            val books = WebBook.exploreBook(source, url)
            if (books.isNotEmpty()) {
                listOf(buildRow(source.bookSourceName, books, source))
            } else {
                fallbackToSearch(source)
            }
        } catch (e: Exception) {
            Log.e(TAG, "探索页加载失败: ${e.message}")
            fallbackToSearch(source)
        }
    }

    private fun fallbackToSearch(source: BookSource): List<ExploreBooksRow> {
        return try {
            val books = WebBook.searchBook(source, "推荐")
            if (books.isNotEmpty()) {
                listOf(buildRow("${source.bookSourceName} 推荐", books, source))
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "搜索回退也失败: ${e.message}")
            emptyList()
        }
    }

    private fun buildRow(title: String, books: List<SearchBook>, source: BookSource): ExploreBooksRow {
        val displayBooks = books.take(20).map { book ->
            val bid = IdCodec.encodeBookId(book.bookUrl, source.bookSourceUrl, "default")
            ExploreDisplayBook(
                id = bid,
                title = book.name,
                author = book.author,
                coverUri = if (!book.coverUrl.isNullOrBlank()) Uri.parse(book.coverUrl) else Uri.EMPTY
            )
        }
        return ExploreBooksRow(title, displayBooks, false, "")
    }
}
