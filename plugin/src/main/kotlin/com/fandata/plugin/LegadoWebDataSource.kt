@file:Suppress("OPT_IN_USAGE")
package com.fandata.plugin

import android.net.Uri
import android.util.Log
import com.fandata.plugin.model.IdCodec
import com.fandata.plugin.utils.ContentUtils
import io.legado.engine.EngineContext
import io.legado.engine.entity.*
import io.legado.engine.webbook.WebBook
import io.nightfish.lightnovelreader.api.book.*
import io.nightfish.lightnovelreader.api.explore.*
import io.nightfish.lightnovelreader.api.web.WebBookDataSource
import io.nightfish.lightnovelreader.api.web.WebDataSource
import io.nightfish.lightnovelreader.api.web.WebDataSourceItem
import io.nightfish.lightnovelreader.api.web.explore.*
import io.nightfish.lightnovelreader.api.web.search.*
import io.nightfish.lightnovelreader.api.util.local
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicBoolean

/**
* Legado 书源数据源 - 使用引擎执行书源规则
* 支持任意 Legado JSON 书源的搜索/探索/详情/目录/正文
*/
@Suppress("unused")
@WebDataSource(name = "FanData书源引擎", provider = "legado_engine")
object LegadoWebDataSource : WebBookDataSource {
private const val TAG = "LegadoWebDS"
private val _isOffLine = MutableStateFlow(false)
private val initialized = AtomicBoolean(false)
override val id: Int = "legado_engine".hashCode()

override fun onLoad() {
Log.i(TAG, "onLoad")
if (initialized.compareAndSet(false, true)) {
try {
val ctx = Class.forName("android.app.ActivityThread")
.getMethod("currentApplication").invoke(null) as? android.content.Context
if (ctx != null) {
val pluginCtx = try {
ctx.createPackageContext(
"com.fandata.plugin",
android.content.Context.CONTEXT_INCLUDE_CODE or android.content.Context.CONTEXT_IGNORE_SECURITY
)
} catch (e: Exception) {
Log.w(TAG, "createPackageContext 失败: ${e.message}")
ctx
}
BookSourceManager.loadFromAssets(pluginCtx)
}
Log.i(TAG, "书源加载完成: ${BookSourceManager.getAll().size} 个")
BookSourceManager.getCurrent()?.let {
Log.i(TAG, "当前书源: ${it.bookSourceName}")
}
} catch (e: Exception) {
Log.e(TAG, "书源加载失败: ${e.message}")
}
}
}

override suspend fun isOffLine() = withContext(Dispatchers.IO) {
_isOffLine.value = BookSourceManager.getCurrent() == null
_isOffLine.value
}
override val offLine get() = _isOffLine.value
override val isOffLineFlow: StateFlow<Boolean> = _isOffLine

// ========== 搜索 ==========
override val searchProvider = object : SearchProvider {
override val searchTypes = listOf(
SearchType("default", "搜索".local(), "输入书名搜索".local())
)
override fun search(searchType: SearchType, keyword: String): Flow<SearchResult> = flow {
val source = BookSourceManager.getCurrent()
if (source == null) {
emit(SearchResult.Empty())
return@flow
}
try {
Log.i(TAG, "搜索: $keyword (源: ${source.bookSourceName})")
                val books = withContext(Dispatchers.IO) {
                    Log.d(TAG, "searchUrl: ${source.searchUrl?.take(200)}")
                    Log.d(TAG, "source.bookSourceUrl: ${source.bookSourceUrl}")
                    Log.d(TAG, "source.bookSourceName: ${source.bookSourceName}")
                    try {
                        // 先测试 AnalyzeUrl 是否能正确处理 JS
                        val testUrl = io.legado.engine.analyze.AnalyzeUrl(
                            mUrl = source.searchUrl ?: "",
                            key = keyword,
                            page = 1,
                            baseUrl = source.bookSourceUrl,
                            source = source
                        )
                        Log.d(TAG, "AnalyzeUrl 结果: url=${testUrl.url.take(200)}, type=${testUrl.type}, dataUrlContent=${testUrl.dataUrlContent?.take(200)}")
                        val response = testUrl.getStrResponse()
                        Log.d(TAG, "Response: code=${response.code()}, body=${response.body?.take(300)}")
                        
                        val books = WebBook.searchBook(source, keyword)
                        Log.d(TAG, "搜索结果: ${books.size} 本")
                        books
                    } catch (e: Exception) {
                        Log.e(TAG, "WebBook.searchBook error: ${e.javaClass.simpleName}: ${e.message}", e)
                        throw e
                    }
                }
_isOffLine.value = false
if (books.isEmpty()) {
emit(SearchResult.Empty())
return@flow
}
for (book in books) {
emit(SearchResult.MultipleBook(ContentUtils.searchBookToBookInfo(book, source)))
}
emit(SearchResult.End())
} catch (e: Exception) {
Log.e(TAG, "搜索失败: ${e.message}")
_isOffLine.value = true
emit(SearchResult.Error(e.message ?: "搜索失败"))
}
}
}

// ========== 探索页 ==========
override val explorePageProvider = object : AbstractDefaultExplorePageProvider() {
private val rowsFlow = MutableStateFlow<List<ExploreBooksRow>>(emptyList())
private val exploreDataSource = object : ExploreTapPageDataSource {
override val title = "发现"
override fun getRowsFlow() = rowsFlow
}

init {
registerTapPage(exploreDataSource)
loadData()
}

private fun loadData() {
Thread {
try {
val source = BookSourceManager.getCurrent()
if (source == null) {
rowsFlow.value = emptyList()
return@Thread
}
val exploreUrl = source.exploreUrl
// 判断 exploreUrl 是否为纯 JS 代码
val isJsOnly = exploreUrl.isNullOrBlank() ||
exploreUrl.trim().startsWith("<js>") ||
exploreUrl.trim().startsWith("@js:")

if (isJsOnly) {
// exploreUrl 是 JS 代码，尝试执行后解析
// 如果失败，回退到搜索模拟
try {
val books = WebBook.exploreBook(source, exploreUrl ?: "")
if (books.isNotEmpty()) {
val displayBooks = books.map { book ->
val bid = IdCodec.encodeBookId(book.bookUrl, source.bookSourceUrl, "default")
ExploreDisplayBook(
id = bid, title = book.name, author = book.author,
coverUri = if (!book.coverUrl.isNullOrBlank()) Uri.parse(book.coverUrl) else Uri.EMPTY
)
}
rowsFlow.value = listOf(ExploreBooksRow(source.bookSourceName, displayBooks, false, ""))
_isOffLine.value = false
return@Thread
}
} catch (e: Exception) {
Log.w(TAG, "JS探索规则执行失败，回退到搜索: ${e.message}")
}
// 回退：用搜索模拟推荐
try {
val books = WebBook.searchBook(source, "推荐")
if (books.isNotEmpty()) {
val displayBooks = books.take(20).map { book ->
val bid = IdCodec.encodeBookId(book.bookUrl, source.bookSourceUrl, "default")
ExploreDisplayBook(
id = bid, title = book.name, author = book.author,
coverUri = if (!book.coverUrl.isNullOrBlank()) Uri.parse(book.coverUrl) else Uri.EMPTY
)
}
rowsFlow.value = listOf(ExploreBooksRow("${source.bookSourceName} 推荐", displayBooks, false, ""))
}
} catch (e: Exception) {
Log.e(TAG, "搜索回退也失败: ${e.message}")
}
} else {
// exploreUrl 是普通 URL，直接请求
try {
val books = WebBook.exploreBook(source, exploreUrl!!)
if (books.isNotEmpty()) {
val displayBooks = books.map { book ->
val bid = IdCodec.encodeBookId(book.bookUrl, source.bookSourceUrl, "default")
ExploreDisplayBook(
id = bid, title = book.name, author = book.author,
coverUri = if (!book.coverUrl.isNullOrBlank()) Uri.parse(book.coverUrl) else Uri.EMPTY
)
}
rowsFlow.value = listOf(ExploreBooksRow(source.bookSourceName, displayBooks, false, ""))
}
} catch (e: Exception) {
Log.e(TAG, "探索页失败: ${e.message}")
}
}
_isOffLine.value = false
} catch (e: Exception) {
Log.e(TAG, "探索页失败: ${e.message}")
_isOffLine.value = true
}
}.start()
}
}

// ========== 书籍详情 ==========
override suspend fun getBookInformation(id: String): BookInformation = withContext(Dispatchers.IO) {
try {
val (bookUrl, sourceUrl, _) = IdCodec.decodeBookId(id)
val source = BookSourceManager.getAll().find { it.bookSourceUrl == sourceUrl }
?: BookSourceManager.getCurrent()
if (source == null || bookUrl.isEmpty()) return@withContext BookInformation.empty()
val book = Book(bookUrl = bookUrl, origin = sourceUrl)
WebBook.getBookInfo(source, book)
_isOffLine.value = false
ContentUtils.bookToBookInfo(book, source)
} catch (e: Exception) {
Log.e(TAG, "详情失败: ${e.message}")
_isOffLine.value = true
BookInformation.empty(id)
}
}

// ========== 目录 ==========
override suspend fun getBookVolumes(id: String): BookVolumes = withContext(Dispatchers.IO) {
try {
val (bookUrl, sourceUrl, _) = IdCodec.decodeBookId(id)
val source = BookSourceManager.getAll().find { it.bookSourceUrl == sourceUrl }
?: BookSourceManager.getCurrent()
if (source == null || bookUrl.isEmpty()) return@withContext BookVolumes.empty(id)
val book = Book(bookUrl = bookUrl, origin = sourceUrl)
val chapters = WebBook.getChapterList(source, book)
_isOffLine.value = false
ContentUtils.chaptersToVolumes(id, chapters, sourceUrl)
} catch (e: Exception) {
Log.e(TAG, "目录失败: ${e.message}")
_isOffLine.value = true
BookVolumes.empty(id)
}
}

// ========== 正文 ==========
override suspend fun getChapterContent(chapterId: String, bookId: String): ChapterContent = withContext(Dispatchers.IO) {
try {
val parts = IdCodec.decodeChapterId(chapterId)
val (bookUrl, sourceUrl, _) = IdCodec.decodeBookId(bookId)
val source = BookSourceManager.getAll().find { it.bookSourceUrl == sourceUrl }
?: BookSourceManager.getCurrent()
if (source == null) return@withContext ChapterContent.empty(chapterId)
val book = Book(bookUrl = bookUrl, origin = sourceUrl)
val chapter = BookChapter(url = parts.itemId, title = parts.title, bookUrl = bookUrl)
val content = WebBook.getBookContent(source, book, chapter)
_isOffLine.value = false
ContentUtils.toChapterContent(chapterId, parts.title, content)
} catch (e: Exception) {
Log.e(TAG, "正文失败: ${e.message}")
_isOffLine.value = true
ChapterContent.empty(chapterId)
}
}

    /**
     * 获取 custom.js 注入脚本（段评功能）
     * 检查 ConfigProvider 开关状态
     */
    fun getCustomJsScript(context: android.content.Context): String? {
        if (!EngineContext.isFeatureEnabled("paragraph_review", false)) return null
        return try {
            context.resources.openRawResource(com.fandata.plugin.R.raw.custom_js)
                .bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.w(TAG, "加载 custom.js 失败: ${e.message}")
            null
        }
    }

    /**
     * 检查功能开关是否启用
     */
    fun isFeatureEnabled(key: String, default: Boolean = false): Boolean {
        return EngineContext.isFeatureEnabled(key, default)
    }
}






