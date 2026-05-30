package io.legado.engine.analyze

import android.util.Log
import io.legado.engine.entity.BookSource
import io.legado.engine.entity.RuleDataInterface
import io.legado.engine.entity.BookChapter
import io.legado.engine.http.HttpHelper
import io.legado.engine.http.StrResponse
import io.legado.engine.js.JsBridge
import io.legado.engine.js.JsExtensions
import io.legado.engine.js.RhinoScriptEngine
import java.util.regex.Pattern

/**
 * URL 构造器 - 移植自 Legado AnalyzeUrl
 * 处理书源中的 URL 规则：JS执行、参数替换、请求方法解析、data: URL
 */
class AnalyzeUrl(
    private val mUrl: String,
    private val key: String? = null,
    private val page: Int? = null,
    private var baseUrl: String = "",
    private val source: BookSource? = null,
    private val ruleData: RuleDataInterface? = null,
    private val chapter: BookChapter? = null
) {
    companion object {
        private const val TAG = "AnalyzeUrl"
        val JS_PATTERN = Pattern.compile("<js>([\\w\\W]*?)</js>|@js:([\\w\\W]*)", Pattern.CASE_INSENSITIVE)
        val EXP_PATTERN = Pattern.compile("\\{\\{([\\w\\W]*?)\\}\\}")
        val httpHelper = HttpHelper()
    }

    var ruleUrl = ""
    var url = ""
    var type: String? = null
    val headerMap = LinkedHashMap<String, String>()
    private var body: String? = null
    private var method = "GET"
    private var jsEngine: RhinoScriptEngine? = null
    private val jsExtensions by lazy {
        JsExtensions(source?.bookSourceUrl ?: "", httpHelper).also {
            it.currentBaseUrl = baseUrl
        }
    }

    // data: URL 解码后的内容
    var dataUrlContent: String? = null
    var dataUrlType: String? = null

    init {
        ruleUrl = mUrl
        Log.d(TAG, "init ruleUrl: ${ruleUrl.take(300)}")
        analyzeJs()
        replaceKeyPage()
        analyzeUrl()
        source?.getHeaderMap()?.let { headerMap.putAll(it) }
    }

    /**
     * 执行 URL 中的 <js>...</js>
     */
    private fun analyzeJs() {
        val m = JS_PATTERN.matcher(ruleUrl)
        val sb = StringBuilder()
        var lastEnd = 0
        var hasJs = false
        while (m.find()) {
            hasJs = true
            sb.append(ruleUrl.substring(lastEnd, m.start()))
            val js = m.group(2) ?: m.group(1) ?: ""
            Log.d(TAG, "analyzeJs 执行: ${js.take(200)}")
            try {
                val result = evalJS(js)
                Log.d(TAG, "analyzeJs 结果: ${result?.toString()?.take(200)}")
                sb.append(result?.toString() ?: "")
            } catch (e: Exception) {
                Log.e(TAG, "analyzeJs 异常: ${e.message}", e)
            }
            lastEnd = m.end()
        }
        sb.append(ruleUrl.substring(lastEnd))
        ruleUrl = sb.toString()
        if (hasJs) {
            Log.d(TAG, "analyzeJs 最终 ruleUrl: ${ruleUrl.take(500)}")
        }
    }

    /**
     * 替换 {{key}}, {{page}}, {{js}} 等参数
     */
    private fun replaceKeyPage() {
        val m = EXP_PATTERN.matcher(ruleUrl)
        val sb = StringBuilder()
        var lastEnd = 0
        while (m.find()) {
            sb.append(ruleUrl.substring(lastEnd, m.start()))
            val expr = m.group(1) ?: ""
            val result = when {
                expr == "key" -> key ?: ""
                expr == "page" -> (page ?: 1).toString()
                expr.startsWith("page-") -> {
                    val offset = expr.removePrefix("page-").toIntOrNull() ?: 0
                    ((page ?: 1) - offset).toString()
                }
                expr.startsWith("page+") -> {
                    val offset = expr.removePrefix("page+").toIntOrNull() ?: 0
                    ((page ?: 1) + offset).toString()
                }
                ruleData != null -> ruleData.getVariable(expr)
                else -> {
                    try {
                        evalJS(expr)?.toString() ?: ""
                    } catch (_: Exception) { "" }
                }
            }
            sb.append(result)
            lastEnd = m.end()
        }
        sb.append(ruleUrl.substring(lastEnd))
        ruleUrl = sb.toString()
        Log.d(TAG, "replaceKeyPage 后: ${ruleUrl.take(500)}")
    }

    /**
     * 解析 URL 结构：方法、URL、请求体
     */
    private fun analyzeUrl() {
        var rule = ruleUrl.trim()
        // 检测请求方法
        when {
            rule.startsWith("@post:", true) -> { method = "POST"; rule = rule.removePrefix("@post:").trim() }
            rule.startsWith("@postjson:", true) -> { method = "POSTJSON"; rule = rule.removePrefix("@postjson:").trim() }
            rule.startsWith("post:", true) -> { method = "POST"; rule = rule.removePrefix("post:").trim() }
            rule.startsWith("postjson:", true) -> { method = "POSTJSON"; rule = rule.removePrefix("postjson:").trim() }
            rule.startsWith("ajax:", true) -> { method = "POST"; rule = rule.removePrefix("ajax:").trim() }
            rule.startsWith("@ajax:", true) -> { method = "POST"; rule = rule.removePrefix("@ajax:").trim() }
        }
        // 分离 URL 和请求体（以,{" 开头的为请求体）
        val bodySep = rule.indexOf(",{\"")
        if (bodySep > 0) {
            url = rule.substring(0, bodySep).trim()
            body = rule.substring(bodySep + 1).trim()
        } else {
            url = rule
        }
        Log.d(TAG, "analyzeUrl: url=${url.take(200)}, method=$method")
        // 处理 data: URL
        if (url.startsWith("data:")) {
            handleDataUrl(url)
            return
        }
        // 编码 URL 中的中文
        url = encodeUrl(url)
    }

    /**
     * 处理 data: URL - 解码 base64 内容
     */
    private fun handleDataUrl(dataUrl: String) {
        try {
            Log.d(TAG, "handleDataUrl: ${dataUrl.take(200)}")
            // data:;base64,<base64>,<extra> 或 data:<type>,<base64>
            val afterData = dataUrl.removePrefix("data:")
            val base64Part: String
            if (afterData.startsWith(";base64,")) {
                val encoded = afterData.removePrefix(";base64,")
                // base64 内容可能后面跟着 ,{"type":...} 这样的额外数据
                base64Part = encoded.substringBefore(",")
            } else {
                // data:text/plain;base64,<base64>
                base64Part = afterData.substringAfter(",").substringBefore(",")
            }
            val decoded = String(
                android.util.Base64.decode(base64Part, android.util.Base64.DEFAULT),
                Charsets.UTF_8
            )
            dataUrlContent = decoded
            type = "data"
            Log.d(TAG, "data URL 解码成功: ${decoded.take(200)}")
        } catch (e: Exception) {
            Log.e(TAG, "data URL 解码失败: ${e.message}")
            dataUrlContent = ""
            type = "data"
        }
    }

    private fun encodeUrl(u: String): String {
        return try {
            val uri = java.net.URI(u)
            uri.toASCIIString()
        } catch (_: Exception) { u }
    }

    /**
     * 执行 HTTP 请求并返回响应
     * 如果是 data: URL，返回包含解码内容的假响应
     */
    fun getStrResponse(): StrResponse {
        Log.d(TAG, "getStrResponse ENTRY: type=$type, dataUrlContent=${dataUrlContent?.take(100)}, url=${url.take(100)}")
        // 如果是 data: URL，返回解码后的内容作为响应
        if (type == "data") {
            Log.d(TAG, "getStrResponse: data URL, content=${dataUrlContent?.take(200)}")
            return StrResponse(
                okhttp3.Response.Builder()
                    .request(okhttp3.Request.Builder().url("https://data.url/").build())
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .build(),
                dataUrlContent ?: ""
            )
        }
        val headers = LinkedHashMap(headerMap)
        Log.d(TAG, "getStrResponse: HTTP $method $url")
        return when (method) {
            "POST" -> {
                val postBody = body ?: "{}"
                httpHelper.post(url, postBody, headers, null)
            }
            "POSTJSON" -> {
                val postBody = body ?: "{}"
                httpHelper.post(url, postBody, headers, null)
            }
            else -> httpHelper.get(url, headers, null)
        }
    }

    /**
     * 执行 JS 代码
     * 注入 java 对象和所有全局函数
     */
    private fun evalJS(script: String): Any? {
        return try {
            val engine = getJsEngine()
            // 更新 currentBaseUrl
            jsExtensions.currentBaseUrl = baseUrl
            // 注入 java 对象（书源中通过 java.xxx() 调用）
            engine.put("java", jsExtensions)
            engine.put("cookie", jsExtensions)
            // 注入其他常用变量
            engine.put("result", "")
            engine.put("baseUrl", baseUrl)
            engine.put("src", source)
            engine.put("key", key ?: "")
            engine.put("page", page ?: 1)
            // 注册全局函数（getVariable, setVariable, BaseUrl 等）
            JsBridge.registerGlobalFunctions(engine, jsExtensions)
            // 执行
            val result = engine.evaluate(script)
            Log.d(TAG, "evalJS result: ${result?.toString()?.take(200)}")
            result
        } catch (e: Exception) {
            Log.e(TAG, "evalJS error: ${e.message}", e)
            null
        }
    }

    private fun getJsEngine(): RhinoScriptEngine = jsEngine ?: RhinoScriptEngine().also { jsEngine = it }
}

