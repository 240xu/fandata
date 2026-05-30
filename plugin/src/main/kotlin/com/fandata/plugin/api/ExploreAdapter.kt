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
 * 
 * 支持:
 * - 普通 URL 探索
 * - JS 规则探索（<js> / @js: 前缀）
 * - data: URL 聚合路由探索
 * - 登录 UI JSON 检测与回退
 */
object ExploreAdapter {
    private const val TAG = "ExploreAdapter"

    /**
     * 加载探索页数据
     * 自动判断 exploreUrl 类型并选择合适的加载方式
     */
    fun loadExploreData(source: BookSource): List<ExploreBooksRow> {
        val exploreUrl = source.exploreUrl
        Log.d(TAG, "loadExploreData: exploreUrl=${'$'}{exploreUrl?.take(200)}")
        
        if (exploreUrl.isNullOrBlank()) {
            Log.d(TAG, "exploreUrl 为空，回退到搜索")
            return fallbackToSearch(source)
        }
        
        val trimmed = exploreUrl.trim()
        
        // JS 规则
        if (trimmed.startsWith("<js>") || trimmed.startsWith("@js:") || trimmed.startsWith("@")) {
            return loadFromJs(source, trimmed)
        }
        
        // data: URL（聚合路由）
        if (trimmed.startsWith("data:", true)) {
            return loadFromDataUrl(source, trimmed)
        }
        
        // 普通 URL
        return loadFromUrl(source, trimmed)
    }

    /**
     * JS 规则探索
     */
    private fun loadFromJs(source: BookSource, exploreUrl: String): List<ExploreBooksRow> {
        return try {
            val books = WebBook.exploreBook(source, exploreUrl)
            if (books.isNotEmpty()) {
                listOf(buildRow(source.bookSourceName, books, source))
            } else {
                Log.w(TAG, "JS 探索返回空结果")
                fallbackToSearch(source)
            }
        } catch (e: Exception) {
            Log.w(TAG, "JS 探索失败: ${'$'}{e.message}")
            fallbackToSearch(source)
        }
    }

    /**
     * data: URL 探索（聚合路由）
     * 
     * 聚合源的 exploreUrl 通常是 data:;base64,...,{"type":"gysearch"}
     * 服务器可能返回:
     * - 正常的书籍列表 JSON
     * - 登录 UI JSON（需要登录时）
     * - 错误信息
     */
    private fun loadFromDataUrl(source: BookSource, exploreUrl: String): List<ExploreBooksRow> {
        return try {
            val books = WebBook.exploreBook(source, exploreUrl)
            if (books.isNotEmpty()) {
                listOf(buildRow(source.bookSourceName, books, source))
            } else {
                Log.w(TAG, "data URL 探索返回空结果")
                fallbackToSearch(source)
            }
        } catch (e: Exception) {
            val msg = e.message ?: ""
            Log.w(TAG, "data URL 探索失败: ${'$'}msg")
            // 检测是否是登录 UI 相关的错误
            if (msg.contains("login", ignoreCase = true) || msg.contains("登录", ignoreCase = true)) {
                Log.i(TAG, "检测到需要登录")
            }
            fallbackToSearch(source)
        }
    }

    /**
     * 普通 URL 探索
     */
    private fun loadFromUrl(source: BookSource, url: String): List<ExploreBooksRow> {
        return try {
            val books = WebBook.exploreBook(source, url)
            if (books.isNotEmpty()) {
                listOf(buildRow(source.bookSourceName, books, source))
            } else {
                Log.w(TAG, "URL 探索返回空结果")
                fallbackToSearch(source)
            }
        } catch (e: Exception) {
            Log.e(TAG, "URL 探索失败: ${'$'}{e.message}")
            fallbackToSearch(source)
        }
    }

    /**
     * 搜索回退 - 使用 "\u63A8\u8350" 关键字模拟推荐
     */
    fun fallbackToSearch(source: BookSource): List<ExploreBooksRow> {
        return try {
            Log.d(TAG, "回退搜索: 推荐")
            val books = WebBook.searchBook(source, "\u63A8\u8350")
            if (books.isNotEmpty()) {
                listOf(buildRow("${'$'}{source.bookSourceName} 推荐", books, source))
            } else {
                Log.w(TAG, "搜索回退也返回空结果")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "搜索回退失败: ${'$'}{e.message}")
            emptyList()
        }
    }

    /**
     * 将 SearchBook 列表转为 LNR ExploreBooksRow
     */
    private fun buildRow(title: String, books: List<SearchBook>, source: BookSource): ExploreBooksRow {
        val displayBooks = books.take(50).map { book ->
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