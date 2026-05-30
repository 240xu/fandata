package io.legado.engine.entity

import com.google.gson.Gson
import io.legado.engine.entity.rule.*

data class BookSource(
    var bookSourceUrl: String = "",
    var bookSourceName: String = "",
    var bookSourceGroup: String? = null,
    var bookSourceType: Int = 0,
    var bookUrlPattern: String? = null,
    var customOrder: Int = 0,
    var enabled: Boolean = true,
    var enabledExplore: Boolean = true,
    override var enabledCookieJar: Boolean? = false,
    override var header: String? = null,
    override var loginUrl: String? = null,
    override var loginUi: String? = null,
    var loginCheckJs: String? = null,
    var bookSourceComment: String? = null,
    var variableComment: String? = null,
    var searchUrl: String? = null,
    var exploreUrl: String? = null,
    var ruleSearch: SearchRule? = null,
    var ruleBookInfo: BookInfoRule? = null,
    var ruleToc: TocRule? = null,
    var ruleContent: ContentRule? = null,
    var ruleExplore: ExploreRule? = null,
    var jsEngine: Int = 0,
    override var concurrentRate: String? = null,
    override var jsLib: String? = null,
    var weight: Int = 0
) : BaseSource {
    companion object {
        val GSON: Gson = Gson()
        fun fromJson(json: String): BookSource? {
            return try { GSON.fromJson(json, BookSource::class.java) } catch (_: Exception) { null }
        }
        fun fromJsonArray(json: String): List<BookSource> {
            return try {
                GSON.fromJson(json, Array<BookSource>::class.java)?.toList() ?: emptyList()
            } catch (_: Exception) {
                fromJson(json)?.let { listOf(it) } ?: emptyList()
            }
        }
    }

    override fun getTag() = bookSourceName
    override fun getKey() = bookSourceUrl

    fun getSearchRule() = ruleSearch ?: SearchRule()
    fun getExploreRule() = ruleExplore ?: ExploreRule()
    fun getBookInfoRule() = ruleBookInfo ?: BookInfoRule()
    fun getTocRule() = ruleToc ?: TocRule()
    fun getContentRule() = ruleContent ?: ContentRule()

    override fun getHeaderMap(hasLogin: Boolean): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        header?.lines()?.forEach { line ->
            val i = line.indexOf(":")
            if (i > 0) map[line.substring(0, i).trim()] = line.substring(i + 1).trim()
        }
        if (!map.containsKey("User-Agent")) {
            map["User-Agent"] = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }
        return map
    }

    // 用户信息缓存（内存级别，由 ConfigProvider 持久化）
    private val loginInfoCache = mutableMapOf<String, String>()
    private val variableCache = mutableMapOf<String, String>()

    override fun getLoginInfo(): String? = loginInfoCache["loginInfo"]
    override fun putLoginInfo(info: String) { loginInfoCache["loginInfo"] = info }
    override fun removeLoginInfo() { loginInfoCache.remove("loginInfo") }
    override fun getVariable(): String = variableCache["variable"] ?: ""
    override fun putVariable(variable: String?) {
        if (variable != null) variableCache["variable"] = variable else variableCache.remove("variable")
    }

    fun getLoginInfoMap(): MutableMap<String, String> {
        val json = getLoginInfo() ?: return mutableMapOf()
        return try {
            val map = GSON.fromJson(json, MutableMap::class.java) as? MutableMap<String, String>
            map ?: mutableMapOf()
        } catch (_: Exception) { mutableMapOf() }
    }

    fun putLoginHeader(header: String) {
        loginInfoCache["loginHeader"] = header
    }
    fun getLoginHeader(): String? = loginInfoCache["loginHeader"]
}

