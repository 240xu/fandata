package io.legado.engine.webbook

import android.util.Log
import io.legado.engine.analyze.AnalyzeUrl
import io.legado.engine.entity.*

object WebBook {
    private const val TAG = "WebBook"

    /**
     * 获取合适的 baseUrl：data URL 时使用 bookSourceUrl
     */
    private fun effectiveBaseUrl(responseUrl: String, sourceUrl: String): String {
        return if (responseUrl.startsWith("https://data.url") || responseUrl.startsWith("data:")) sourceUrl else responseUrl
    }

    fun searchBook(bookSource: BookSource, key: String, page: Int = 1): ArrayList<SearchBook> {
        val searchUrl = bookSource.searchUrl ?: throw Exception("搜索URL为空")
        val ruleData = RuleData()
        val analyzeUrl = AnalyzeUrl(
            mUrl = searchUrl, key = key, page = page,
            baseUrl = bookSource.bookSourceUrl, source = bookSource, ruleData = ruleData
        )
        val response = analyzeUrl.getStrResponse()
        Log.d(TAG, "searchBook AFTER getStrResponse: response.body=${response.body?.take(200)}, analyzeUrl.type=${analyzeUrl.type}, analyzeUrl.dataUrlContent=${analyzeUrl.dataUrlContent?.take(200)}")
        val body = response.body
        if (body == null && analyzeUrl.type != "data") {
            throw Exception("搜索请求失败: ${response.code()}")
        }
        Log.d(TAG, "searchBook body=, type=, dataUrlContent=")
        val effectiveUrl = effectiveBaseUrl(response.url, bookSource.bookSourceUrl)
        return BookList.analyzeBookList(bookSource, ruleData, body ?: "", effectiveUrl, isSearch = true)
    }

    fun exploreBook(bookSource: BookSource, url: String, page: Int = 1): ArrayList<SearchBook> {
        val ruleData = RuleData()
        val analyzeUrl = AnalyzeUrl(
            mUrl = url, page = page,
            baseUrl = bookSource.bookSourceUrl, source = bookSource, ruleData = ruleData
        )
        val response = analyzeUrl.getStrResponse()
        Log.d(TAG, "searchBook AFTER getStrResponse: response.body=${response.body?.take(200)}, analyzeUrl.type=${analyzeUrl.type}, analyzeUrl.dataUrlContent=${analyzeUrl.dataUrlContent?.take(200)}")
        val body = response.body
        if (body == null && analyzeUrl.type != "data") {
            throw Exception("发现请求失败: ${response.code()}")
        }
        val effectiveUrl = effectiveBaseUrl(response.url, bookSource.bookSourceUrl)
        return BookList.analyzeBookList(bookSource, ruleData, body ?: "", effectiveUrl, isSearch = false)
    }

    fun getBookInfo(bookSource: BookSource, book: Book): Book {
        val ruleData = RuleData()
        val analyzeUrl = AnalyzeUrl(
            mUrl = book.bookUrl, baseUrl = book.bookUrl,
            source = bookSource, ruleData = ruleData
        )
        val response = analyzeUrl.getStrResponse()
        Log.d(TAG, "searchBook AFTER getStrResponse: response.body=${response.body?.take(200)}, analyzeUrl.type=${analyzeUrl.type}, analyzeUrl.dataUrlContent=${analyzeUrl.dataUrlContent?.take(200)}")
        if (response.isSuccessful()) {
            book.infoHtml = response.body
            BookInfo.analyzeBookInfo(bookSource, ruleData, book, response.body, response.url)
        }
        return book
    }

    fun getChapterList(bookSource: BookSource, book: Book): ArrayList<BookChapter> {
        val ruleData = RuleData()
        val tocUrl = book.tocUrl.ifBlank { book.bookUrl }
        val analyzeUrl = AnalyzeUrl(
            mUrl = tocUrl, baseUrl = book.bookUrl,
            source = bookSource, ruleData = ruleData
        )
        val response = analyzeUrl.getStrResponse()
        Log.d(TAG, "searchBook AFTER getStrResponse: response.body=${response.body?.take(200)}, analyzeUrl.type=${analyzeUrl.type}, analyzeUrl.dataUrlContent=${analyzeUrl.dataUrlContent?.take(200)}")
        if (!response.isSuccessful() && analyzeUrl.type != "data") {
            throw Exception("目录请求失败: ${response.code()}")
        }
        val effectiveUrl = effectiveBaseUrl(response.url, bookSource.bookSourceUrl)
        return BookChapterList.analyzeChapterList(bookSource, ruleData, book, response.body ?: "", effectiveUrl)
    }

    fun getBookContent(bookSource: BookSource, book: Book, bookChapter: BookChapter): String {
        val analyzeUrl = AnalyzeUrl(
            mUrl = bookChapter.getAbsoluteURL(), baseUrl = book.tocUrl,
            source = bookSource, chapter = bookChapter
        )
        val response = analyzeUrl.getStrResponse()
        Log.d(TAG, "searchBook AFTER getStrResponse: response.body=${response.body?.take(200)}, analyzeUrl.type=${analyzeUrl.type}, analyzeUrl.dataUrlContent=${analyzeUrl.dataUrlContent?.take(200)}")
        if (!response.isSuccessful() && analyzeUrl.type != "data") {
            throw Exception("正文请求失败: ${response.code()}")
        }
        return BookContent.analyzeContent(bookSource, book, bookChapter, response.body ?: "", response.url)
    }
}

