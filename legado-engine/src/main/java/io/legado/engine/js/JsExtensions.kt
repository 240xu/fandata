package io.legado.engine.js

import android.util.Base64
import android.util.Log
import io.legado.engine.http.HttpHelper
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * JS 扩展方法 - 提供给书源 JS 代码的 java.xxx() 函数
 * 移植自 Legado 的 JsExtensions
 */
class JsExtensions(
    private val sourceUrl: String = "",
    private val httpHelper: HttpHelper? = null
) {
    companion object {
        private const val TAG = "JsExtensions"
    }

    // Cookie 存储（外部可注入）
    var cookieStore: MutableMap<String, String> = mutableMapOf()

    // 变量存储（书源变量）
    private val variables = mutableMapOf<String, Any?>()
    private var cloudSettings: Map<String, Any?>? = null

    // 当前 HTTP 请求的基础 URL（用于 request() 函数）
    var currentBaseUrl: String = ""
    /** 存储最后一次 request/ajax 的响应内容（供链式规则使用） */
    var lastResponse: String? = null

    // ==================== HTTP 请求 ====================

    /**
     * ajax() - GET 请求，返回响应体
     * 书源 JS 中最常用的方法
     */
    fun ajax(url: String): String {
        // 处理 "url, options" 格式（番茄等书源使用）
        if (url.contains(", {") || url.contains(",{")) {
            val commaIdx = url.indexOf(", {").let { if (it < 0) url.indexOf(",{") else it }
            if (commaIdx > 0) {
                val realUrlPart = url.substring(0, commaIdx).trim()
                val optionsPart = url.substring(commaIdx + 1).trim()
                return ajaxWithOptions(realUrlPart, optionsPart)
            }
        }
        return try {
            val realUrl = resolveUrl(url)
            Log.d(TAG, "ajax GET: $realUrl")
            val req = Request.Builder().url(realUrl).get().build()
            executeRequest(req)
        } catch (e: Exception) {
            Log.e(TAG, "ajax error: ${e.message}")
            ""
        }
    }

    /**
     * ajax(url, options) - 带选项的请求
     * options 可以是 JSON 字符串，包含 method, headers, body 等
     */
    fun ajax(url: String, options: String): String {
        return ajaxWithOptions(url, options)
    }

    private fun ajaxWithOptions(url: String, options: String): String {
        return try {
            val realUrl = resolveUrl(url)
            Log.d(TAG, "ajaxWithOptions: $realUrl, options=${options.take(200)}")
            val opts = try {
                com.google.gson.Gson().fromJson(options, Map::class.java) as? Map<String, Any?>
            } catch (_: Exception) { null }

            val method = (opts?.get("method")?.toString() ?: "GET").uppercase()
            val headers = LinkedHashMap<String, String>()
            @Suppress("UNCHECKED_CAST")
            val headerMap = opts?.get("headers") as? Map<String, Any?>
            headerMap?.forEach { (k, v) -> headers[k] = v.toString() }

            val reqBuilder = Request.Builder().url(realUrl)
            headers.forEach { (k, v) -> reqBuilder.addHeader(k, v) }

            when (method) {
                "POST" -> {
                    val bodyStr = opts?.get("body")?.toString() ?: "{}"
                    val mediaType = "application/json; charset=utf-8".toMediaType()
                    reqBuilder.post(bodyStr.toRequestBody(mediaType))
                }
            }
            executeRequest(reqBuilder.build())
        } catch (e: Exception) {
            Log.e(TAG, "ajaxWithOptions error: ${e.message}")
            ""
        }
    }

    /**
     * request() - GET 请求
     */
    fun request(url: String): String {
        val resp = ajax(url)
        lastResponse = resp
        return resp
    }

    /**
     * request(url, method, body) - 带方法和请求体
     */
    fun request(url: String, method: String, body: Any?): String {
        return try {
            val realUrl = resolveUrl(url)
            val bodyStr = when (body) {
                is String -> body
                is Map<*, *> -> com.google.gson.Gson().toJson(body)
                else -> "{}"
            }
            Log.d(TAG, "request $method: $realUrl")
            when (method.uppercase()) {
                "POST" -> {
                    val mediaType = "application/json; charset=utf-8".toMediaType()
                    val reqBody = bodyStr.toRequestBody(mediaType)
                    val req = Request.Builder().url(realUrl).post(reqBody).build()
                    executeRequest(req)
                }
                else -> ajax(url)
            }
        } catch (e: Exception) {
            Log.e(TAG, "request error: ${e.message}")
            ""
        }
    }

    /**
     * post() - POST 请求
     */
    fun post(url: String, body: String): String {
        return try {
            val realUrl = resolveUrl(url)
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val reqBody = body.toRequestBody(mediaType)
            val req = Request.Builder().url(realUrl).post(reqBody).build()
            executeRequest(req)
        } catch (e: Exception) {
            Log.e(TAG, "post error: ${e.message}")
            ""
        }
    }

    private fun executeRequest(req: Request): String {
        val client = httpHelper?.client ?: HttpHelper().client
        return client.newCall(req).execute().use { response ->
            val body = response.body?.string() ?: ""
            // 自动保存 Set-Cookie
            response.headers("Set-Cookie").forEach { cookie ->
                val domain = req.url.host
                cookieStore[domain] = cookie.substringBefore(";")
            }
            body
        }
    }

    // ==================== 编码/解码 ====================

    fun base64Encode(str: String): String {
        return try {
            Base64.encodeToString(str.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        } catch (_: Exception) { "" }
    }

    fun base64Decode(str: String): String {
        return try {
            String(Base64.decode(str, Base64.NO_WRAP), Charsets.UTF_8)
        } catch (_: Exception) { "" }
    }

    fun hexDecodeToString(hex: String): String {
        return try {
            val bytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            String(bytes, Charsets.UTF_8)
        } catch (_: Exception) {
            // 如果不是有效 hex，检查是否已经是可读字符串
            // 这种情况发生在 data:URL 解码后的 JSON 直接作为 result 传入
            if (hex.trimStart().let { it.startsWith("{") || it.startsWith("[") }) hex
            else try { base64Decode(hex) } catch (_: Exception) { hex }
        }
    }

    fun stringToHex(str: String): String {
        return try {
            str.toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it) }
        } catch (_: Exception) { "" }
    }

    // ==================== Cookie 管理 ====================

    fun getCookie(domain: String): String {
        return httpHelper?.cookieStore?.get(domain) ?: cookieStore[domain] ?: ""
    }

fun getCookie(domain: String, key: String): String {
        // 从 httpHelper 的 cookieStore 获取
        val cookies = httpHelper?.cookieStore?.get(domain) ?: cookieStore[domain] ?: return ""
        return try {
            cookies.split(";").forEach { c ->
                val parts = c.trim().split("=", limit = 2)
                if (parts.size == 2 && parts[0].trim() == key) {
                    return parts[1].trim()
                }
            }
            ""
        } catch (_: Exception) { "" }
    }

    fun setCookie(domain: String, cookie: String) {
        cookieStore[domain] = cookie
        httpHelper?.cookieStore?.put(domain, cookie)
    }

    // ==================== 变量管理 ====================

    fun getVariable(key: String): Any? {
        val v = variables[key]
        Log.d(TAG, "getVariable($key) = ${v?.toString()?.take(100)}")
        return v
    }

    fun setVariable(key: String, value: Any?) {
        Log.d(TAG, "setVariable($key, ${value?.toString()?.take(100)})")
        variables[key] = value
    }

    // ==================== 辅助函数 ====================

    /**
     * BaseUrl() - 获取书源基础 URL
     * 书源 JS 中频繁使用
     */
    fun BaseUrl(): String = sourceUrl

    /**
     * checkEnv() - 检查运行环境
     * 返回 "安卓" / "苹果" 等
     */
    fun checkEnv(): String = "安卓"

    /**
     * getCloudSettings() - 获取云端配置
     */
    fun getCloudSettings(force: Boolean = false): Map<String, Any?>? {
        if (cloudSettings != null && !force) return cloudSettings
        try {
            val base = if (sourceUrl.startsWith("http")) sourceUrl.trimEnd('/')
                       else httpHelper?.cachedBaseUrl?.trimEnd('/') ?: ""
            if (base.isEmpty()) {
                Log.w(TAG, "getCloudSettings: no valid base URL")
                return cloudSettings
            }
            val url = "${base}/static/source_config/config.json"
            Log.d(TAG, "getCloudSettings: ${url.take(100)}")
            val response = ajax(url)
            if (response.isNotBlank()) {
                cloudSettings = com.google.gson.Gson().fromJson(response, Map::class.java) as? Map<String, Any?>
                variables["云端配置"] = cloudSettings
            }
        } catch (e: Exception) {
            Log.e(TAG, "getCloudSettings error: ${e.message}")
        }
        return cloudSettings
    }

    /**
     * getSourceUrl() - 获取书源 URL
     */
    fun getSourceUrl(): String = sourceUrl

    /**
     * startBrowser() - 在浏览器中打开 URL
     */
    fun startBrowser(url: String, title: String = ""): String = url

    fun httpGet(url: String): String = ajax(url)

    /**
     * parseJson() - 解析 JSON 字符串
     */
    fun parseJson(s: String): Any? {
        return try {
            com.google.gson.Gson().fromJson(s, Any::class.java)
        } catch (_: Exception) { null }
    }

    /**
     * toast() - 显示 Toast
     */
    fun toast(msg: String) {
        Log.i(TAG, "toast: $msg")
    }

    /**
     * longToast() - 显示长 Toast
     */
    fun longToast(msg: String) {
        Log.i(TAG, "longToast: $msg")
    }

    /**
     * log() - 日志
     */
    fun log(msg: String) {
        Log.i(TAG, "log: $msg")
    }

    // ==================== 内部方法 ====================

    /**
     * 解析相对 URL
     */
    private fun resolveUrl(url: String): String {
        if (url.isBlank()) return url
        if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("data:")) return url
        var base = currentBaseUrl.ifBlank { "" }
        if (base.isBlank() || !base.startsWith("http")) base = httpHelper?.cachedBaseUrl ?: ""
        if (base.isBlank() || !base.startsWith("http")) { try { getCloudSettings(false); base = httpHelper?.cachedBaseUrl ?: "" } catch (_: Exception) {} }
        if (base.isBlank()) return url
        return try {
            java.net.URL(java.net.URL(base), url).toString()
        } catch (_: Exception) { url }
    }

    // ==================== 额外 API（光遇聚合等复杂源使用） ====================

    private val store = mutableMapOf<String, Any?>()

    fun get(key: String): Any? = store[key]

    fun put(key: String, value: Any?): Any? {
        store[key] = value
        return value
    }

    fun timeFormat(dateOrTimestamp: Any?): String {
        return try {
            val millis = when (dateOrTimestamp) {
                is Number -> dateOrTimestamp.toLong()
                is String -> dateOrTimestamp.toLongOrNull() ?: System.currentTimeMillis()
                else -> System.currentTimeMillis()
            }
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(millis))
        } catch (_: Exception) { "" }
    }

    fun androidId(): String = "unknown"
    fun deviceID(): String = "unknown"
    fun getWebViewUA(): String = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36"

    fun startBrowserAwait(url: String, title: String = ""): String {
        Log.i(TAG, "startBrowserAwait: $url ($title)")
        return url
    }

    fun showBrowser(url: String): String = startBrowserAwait(url)
    fun showReadingBrowser(url: String): String = startBrowserAwait(url)
    fun startBrowserDp(url: String, title: String = ""): String = startBrowserAwait(url, title)

    fun openVideoPlayer(url: String) {
        Log.i(TAG, "openVideoPlayer: $url")
    }

    fun qread(data: Any?) {
        Log.d(TAG, "qread called")
    }

    fun refreshExplore() {
        Log.d(TAG, "refreshExplore called")
    }
}





