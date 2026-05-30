package io.legado.engine.analyze

import android.util.Log
import io.legado.engine.entity.BookSource
import io.legado.engine.js.JsBridge
import io.legado.engine.js.JsExtensions
import io.legado.engine.js.RhinoScriptEngine
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import java.util.regex.Pattern

/**
 * 核心规则分析引擎 - 移植自 Legado AnalyzeRule
 * 支持: CSS/XPath/JSONPath/正则/JS/链式规则/逻辑操作
 */
class AnalyzeRule(
    private var ruleData: io.legado.engine.entity.RuleDataInterface? = null,
    private val source: BookSource? = null,
    private var content: Any? = null,
    private var baseUrl: String? = null
) {
    companion object {
        private const val TAG = "AnalyzeRule"
        val JS_PATTERN = Pattern.compile("<js>([\\w\\W]*?)</js>|@js:([\\w\\W]*)", Pattern.CASE_INSENSITIVE)
        val EXP_PATTERN = Pattern.compile("\\{\\{([\\w\\W]*?)\\}\\}")
    }

    private var isJSON = false
    private var analyzeByXPath: AnalyzeByXPath? = null
    private var analyzeByJSoup: AnalyzeByJSoup? = null
    private var analyzeByJSonPath: AnalyzeByJSonPath? = null
    private var jsEngine: RhinoScriptEngine? = null
    private val jsExtensions: JsExtensions by lazy {
        JsExtensions(source?.bookSourceUrl ?: "", AnalyzeUrl.httpHelper).also {
            it.currentBaseUrl = baseUrl ?: ""
        }
    }
    private val stringRuleCache = hashMapOf<String, List<SourceRule>>()

    fun setContent(content: Any?, baseUrl: String? = null): AnalyzeRule {
        this.content = content
        isJSON = when (content) {
            is Node -> false
            else -> content.toString().let { s -> s.trimStart().let { it.startsWith("{") || it.startsWith("[") } }
        }
        baseUrl?.let { this.baseUrl = it }
        analyzeByXPath = null; analyzeByJSoup = null; analyzeByJSonPath = null
        return this
    }

    fun setBaseUrl(url: String?): AnalyzeRule { url?.let { baseUrl = it }; return this }
    fun setRuleData(data: io.legado.engine.entity.RuleDataInterface?) { ruleData = data }

    fun getString(ruleStr: String?, isUrl: Boolean = false): String {
        if (ruleStr.isNullOrBlank()) return ""
        return try {
            val rules = splitSourceRule(ruleStr)
            getStringInternal(rules, isUrl = isUrl)
        } catch (e: Exception) {
            Log.e(TAG, "getString error: ${e.message}")
            ""
        }
    }

    private fun getStringInternal(rules: List<SourceRule>, isUrl: Boolean = false): String {
        val result = StringBuilder()
        var curContent = this.content
        for (sr in rules) {
            val r = when (sr.mode) {
                Mode.Js -> evalJS(sr.rule, curContent)?.toString() ?: ""
                Mode.Regex -> AnalyzeByRegex.getString(curContent?.toString() ?: "", sr.rule)
                Mode.XPath -> {
                    val c = curContent
                    if (c != null && (c is Node || c is Document)) getAX(c).getString(sr.rule) else ""
                }
                Mode.Json -> getAJ(curContent ?: "").getString(sr.rule)
                Mode.Default -> {
                    if (isJSON) getAJ(curContent ?: "").getString(sr.rule)
                    else {
                        val c = curContent
                        when (c) {
                            is Element -> getAJoup(c).getString(sr.rule)
                            is Document -> getAJoup(c).getString(sr.rule)
                            else -> if (c != null) getAJoup(Jsoup.parse(c.toString())).getString(sr.rule) else ""
                        }
                    }
                }
            }
            result.append(r)
            // 更新 curContent 为当前规则结果（链式传递）
            if (r.isNotBlank()) curContent = r
            sr.replaceRegex?.let { regex ->
                try { result.replace(0, result.length, result.toString().replace(Regex(regex), sr.replacement ?: "")) } catch (_: Exception) {}
            }
        }
        return result.toString()
    }

    fun getStringList(ruleStr: String?): List<String> {
        if (ruleStr.isNullOrBlank()) return emptyList()
        return try {
            val rules = splitSourceRule(ruleStr)
            val c = this.content ?: return emptyList()
            for (sr in rules) {
                return when (sr.mode) {
                    Mode.Js -> {
                        val r = evalJS(sr.rule, c)
                        if (r is List<*>) r.filterIsInstance<String>()
                        else if (r != null) listOf(r.toString()) else emptyList()
                    }
                    Mode.Json -> getAJ(c).getStringList(sr.rule)
                    Mode.XPath -> if (c is Node) getAX(c).getStringList(sr.rule) else emptyList()
                    Mode.Regex -> AnalyzeByRegex.getStringList(c.toString(), sr.rule)
                    Mode.Default -> if (isJSON) getAJ(c).getStringList(sr.rule)
                    else if (c is Node) getAJoup(c).getStringList(sr.rule)
                    else getAJoup(Jsoup.parse(c.toString())).getStringList(sr.rule)
                }
            }
            emptyList()
        } catch (_: Exception) { emptyList() }
    }

    /**
     * 获取元素列表 - 支持 JS 链式规则
     * 如 <js>request(url)</js>@$.data
     */
    fun getElements(ruleStr: String?): List<Any> {
        if (ruleStr.isNullOrBlank()) return emptyList()
        val c = this.content ?: return emptyList()
        return try {
            val rules = splitSourceRule(ruleStr!!)
            var curContent: Any? = c
            for (sr in rules) {
                when (sr.mode) {
                    Mode.Js -> {
                        val jsResult = evalJS(sr.rule, curContent)
                        // 如果 JS 返回 null/undefined，检查 lastResponse
                        curContent = jsResult?.toString()?.takeIf { it != "undefined" && it != "null" }
                            ?: jsExtensions.lastResponse
                            ?: curContent
                    }
                    Mode.Json -> {
                        val obj = getAJ(curContent ?: "").getObject(sr.rule)
                        curContent = when (obj) {
                            is List<*> -> return obj.filterNotNull()
                            is Map<*, *> -> obj
                            else -> obj
                        }
                    }
                    Mode.Default -> {
                        if (curContent?.toString()?.trimStart()?.let { it.startsWith("{") || it.startsWith("[") } == true) {
                            val obj = getAJ(curContent!!).getObject(sr.rule)
                            curContent = when (obj) {
                                is List<*> -> return obj.filterNotNull()
                                is Map<*, *> -> obj
                                else -> obj
                            }
                        } else {
                            val doc = when (curContent) {
                                is Document -> curContent as Document
                                is Element -> (curContent as Element).ownerDocument() ?: Jsoup.parse("")
                                else -> Jsoup.parse(curContent.toString())
                            }
                            return getAJoup(doc).getElements(sr.rule).toList()
                        }
                    }
                    else -> {}
                }
            }
            // 最终结果
            when (curContent) {
                is List<*> -> curContent.filterNotNull()
                is Map<*, *> -> listOf(curContent)
                else -> {
                    val s = curContent?.toString() ?: return emptyList()
                    if (s.trimStart().let { it.startsWith("{") || it.startsWith("[") }) {
                        val obj = getAJ(s).getObject("$")
                        when (obj) {
                            is List<*> -> obj.filterNotNull()
                            is Map<*, *> -> listOf(obj)
                            else -> emptyList()
                        }
                    } else emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getElements error: ${e.message}")
            emptyList()
        }
    }

    /**
     * 执行内嵌 JS - 支持 lastResponse 回退
     */
    fun evalJS(script: String, result: Any? = null): Any? {
        return try {
            val engine = getJsEngine()
            jsExtensions.currentBaseUrl = baseUrl ?: ""
            jsExtensions.lastResponse = null
            engine.put("java", jsExtensions)
            engine.put("cookie", jsExtensions)
            engine.put("result", result)
            engine.put("baseUrl", baseUrl ?: "")
            engine.put("src", source)
            engine.put("source", source)
            engine.put("book", ruleData)
            JsBridge.registerGlobalFunctions(engine, jsExtensions)
            // 加载 jsLib（书源中定义的公共函数）
            source?.jsLib?.let { engine.evalJsLib(it) }
            val jsResult = engine.evaluate(script)
            // 如果 JS 返回 null/undefined，检查 lastResponse
            if (jsResult == null) {
                jsExtensions.lastResponse
            } else {
                jsResult
            }
        } catch (e: Exception) {
            Log.e(TAG, "evalJS error: ${e.message}")
            null
        }
    }

    private fun getJsEngine(): RhinoScriptEngine = jsEngine ?: RhinoScriptEngine().also { jsEngine = it }
    private fun getAJoup(o: Any): AnalyzeByJSoup = if (o !== content) AnalyzeByJSoup(o) else (analyzeByJSoup ?: AnalyzeByJSoup(content!!).also { analyzeByJSoup = it })
    private fun getAX(o: Any): AnalyzeByXPath = if (o !== content) AnalyzeByXPath(o) else (analyzeByXPath ?: AnalyzeByXPath(content!!).also { analyzeByXPath = it })
    private fun getAJ(o: Any): AnalyzeByJSonPath = if (o !== content) AnalyzeByJSonPath(o) else (analyzeByJSonPath ?: AnalyzeByJSonPath(content!!).also { analyzeByJSonPath = it })

    private fun splitSourceRule(ruleStr: String): List<SourceRule> {
        stringRuleCache[ruleStr]?.let { return it }
        val rules = mutableListOf<SourceRule>()
        // 先提取 <js>...</js> 块，避免在 @ 分割时破坏 JS
        val jsPattern = Pattern.compile("<js>([\\w\\W]*?)</js>")
        val jsMatcher = jsPattern.matcher(ruleStr)
        var lastEnd = 0
        val parts = mutableListOf<String>()
        while (jsMatcher.find()) {
            // @ 前的非JS部分
            val before = ruleStr.substring(lastEnd, jsMatcher.start())
            if (before.isNotBlank()) {
                before.split("@").filter { it.isNotBlank() }.forEach { parts.add(it.trim()) }
            }
            // JS 部分（保留标签）
            parts.add(jsMatcher.group())
            lastEnd = jsMatcher.end()
        }
        val after = ruleStr.substring(lastEnd)
        if (after.isNotBlank()) {
            after.split("@").filter { it.isNotBlank() }.forEach { parts.add(it.trim()) }
        }

        for (part in parts) {
            val t = part.trim()
            if (t.isEmpty()) continue
            when {
                t.startsWith("<js>", true) && t.endsWith("</js>", true) ->
                    rules.add(SourceRule(t.removePrefix("<js>").removeSuffix("</js>").trim(), Mode.Js))
                t.startsWith("##") -> {
                    val rp = t.removePrefix("##").split("##", limit = 2)
                    rules.add(SourceRule(rp[0], Mode.Regex, rp.getOrNull(1)?.split("##", limit = 2)?.getOrNull(0), rp.getOrNull(1)?.split("##", limit = 2)?.getOrNull(1)))
                }
                t.startsWith("xpath:", true) -> rules.add(SourceRule(t.removePrefix("xpath:"), Mode.XPath))
                t.startsWith("json:", true) -> rules.add(SourceRule(t.removePrefix("json:"), Mode.Json))
                t.startsWith("@json:", true) -> rules.add(SourceRule(t.removePrefix("@json:"), Mode.Json))
                t.startsWith("css:", true) -> rules.add(SourceRule(t.removePrefix("css:"), Mode.Default))
                t.startsWith("@css:", true) -> rules.add(SourceRule(t.removePrefix("@css:"), Mode.Default))
                else -> rules.add(SourceRule(t, Mode.Default))
            }
        }
        if (rules.isEmpty()) rules.add(SourceRule(ruleStr, Mode.Default))
        val result = rules.toList()
        stringRuleCache[ruleStr] = result
        return result
    }

    data class SourceRule(val rule: String, val mode: Mode, val replaceRegex: String? = null, val replacement: String? = null)
    enum class Mode { Default, Js, Regex, XPath, Json }
}

