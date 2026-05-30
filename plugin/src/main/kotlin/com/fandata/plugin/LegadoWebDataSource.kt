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
* Legado ��Դ����Դ - ʹ������ִ����Դ����
* ֧������ Legado JSON ��Դ������/̽��/����/Ŀ¼/����
*/
@Suppress("unused")
@WebDataSource(name = "FanData��Դ����", provider = "legado_engine")
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
Log.w(TAG, "createPackageContext ʧ��: ${e.message}")
ctx
}
BookSourceManager.loadFromAssets(pluginCtx)
}
Log.i(TAG, "��Դ�������: ${BookSourceManager.getAll().size} ��")
BookSourceManager.getCurrent()?.let {
Log.i(TAG, "��ǰ��Դ: ${it.bookSourceName}")
}
} catch (e: Exception) {
Log.e(TAG, "��Դ����ʧ��: ${e.message}")
}
}
}

override suspend fun isOffLine() = withContext(Dispatchers.IO) {
_isOffLine.value = BookSourceManager.getCurrent() == null
_isOffLine.value
}
override val offLine get() = _isOffLine.value
override val isOffLineFlow: StateFlow<Boolean> = _isOffLine

// ========== ���� ==========
override val searchProvider = object : SearchProvider {
override val searchTypes = listOf(
SearchType("default", "����".local(), "������������".local())
)
override fun search(searchType: SearchType, keyword: String): Flow<SearchResult> = flow {
val source = BookSourceManager.getCurrent()
if (source == null) {
emit(SearchResult.Empty())
return@flow
}
try {
Log.i(TAG, "����: $keyword (Դ: ${source.bookSourceName})")
                val books = withContext(Dispatchers.IO) {
                    Log.d(TAG, "searchUrl: ${source.searchUrl?.take(200)}")
                    Log.d(TAG, "source.bookSourceUrl: ${source.bookSourceUrl}")
                    Log.d(TAG, "source.bookSourceName: ${source.bookSourceName}")
                    try {
                        // �Ȳ��� AnalyzeUrl �Ƿ�����ȷ���� JS
                        val testUrl = io.legado.engine.analyze.AnalyzeUrl(
                            mUrl = source.searchUrl ?: "",
                            key = keyword,
                            page = 1,
                            baseUrl = source.bookSourceUrl,
                            source = source
                        )
                        Log.d(TAG, "AnalyzeUrl ���: url=${testUrl.url.take(200)}, type=${testUrl.type}, dataUrlContent=${testUrl.dataUrlContent?.take(200)}")
                        val response = testUrl.getStrResponse()
                        Log.d(TAG, "Response: code=${response.code()}, body=${response.body?.take(300)}")
                        
                        val books = WebBook.searchBook(source, keyword)
                        Log.d(TAG, "�������: ${books.size} ��")
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
Log.e(TAG, "����ʧ��: ${e.message}")
_isOffLine.value = true
emit(SearchResult.Error(e.message ?: "����ʧ��"))
}
}
}

// ========== ̽��ҳ ==========
override val explorePageProvider = object : AbstractDefaultExplorePageProvider() {
private val rowsFlow = MutableStateFlow<List<ExploreBooksRow>>(emptyList())
private val exploreDataSource = object : ExploreTapPageDataSource {
override val title = "����"
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
// �ж� exploreUrl �Ƿ�Ϊ�� JS ����
val isJsOnly = exploreUrl.isNullOrBlank() ||
exploreUrl.trim().startsWith("<js>") ||
exploreUrl.trim().startsWith("@js:")

if (isJsOnly) {
// exploreUrl �� JS ���룬����ִ�к����
// ���ʧ�ܣ����˵�����ģ��
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
Log.w(TAG, "JS̽������ִ��ʧ�ܣ����˵�����: ${e.message}")
}
// ���ˣ�������ģ��推荐
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
Log.e(TAG, "��������Ҳʧ��: ${e.message}")
}
} else {
// exploreUrl ����ͨ URL��ֱ������
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
Log.e(TAG, "̽��ҳʧ��: ${e.message}")
}
}
_isOffLine.value = false
} catch (e: Exception) {
Log.e(TAG, "̽��ҳʧ��: ${e.message}")
_isOffLine.value = true
}
}.start()
}
}

// ========== �鼮���� ==========
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
Log.e(TAG, "����ʧ��: ${e.message}")
_isOffLine.value = true
BookInformation.empty(id)
}
}

// ========== Ŀ¼ ==========
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
Log.e(TAG, "Ŀ¼ʧ��: ${e.message}")
_isOffLine.value = true
BookVolumes.empty(id)
}
}

// ========== ���� ==========
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
Log.e(TAG, "����ʧ��: ${e.message}")
_isOffLine.value = true
ChapterContent.empty(chapterId)
}
}

    /**
     * ��ȡ custom.js ע��ű����������ܣ�
     * ��� ConfigProvider ����״̬
     */
    fun getCustomJsScript(context: android.content.Context): String? {
        if (!EngineContext.isFeatureEnabled("paragraph_review", false)) return null
        return try {
            context.resources.openRawResource(com.fandata.plugin.R.raw.custom_js)
                .bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.w(TAG, "���� custom.js ʧ��: ${e.message}")
            null
        }
    }

    /**
     * ��鹦�ܿ����Ƿ�����
     */
    fun isFeatureEnabled(key: String, default: Boolean = false): Boolean {
        return EngineContext.isFeatureEnabled(key, default)
    }
}






