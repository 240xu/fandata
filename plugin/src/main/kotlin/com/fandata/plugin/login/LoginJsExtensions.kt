package com.fandata.plugin.login

import android.util.Log
import io.legado.engine.entity.BookSource
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 登录 JS 桥接方法 - 移植自 Legado SourceLoginJsExtensions
 * 供书源 loginUrl 中的 JS 代码调用
 * 
 * 书源中常见的调用模式：
 *   java.toast("...")
 *   java.getCookie("domain")
 *   java.startBrowserAwait("url", "title")
 *   cookie.getCookie("domain")
 *   cookie.getKey("domain", "key")
 *   cookie.removeCookie("domain")
 *   source.getVariable()
 *   source.setVariable(json)
 *   source.getLoginInfoMap()
 *   java.put("key", value)
 *   java.get("key")
 */
class LoginJsExtensions(private val callback: LoginHelper.LoginCallback?) {
    companion object { 
        private const val TAG = "LoginJsExt"
        // 变量存储（跨调用共享）
        private val variables = mutableMapOf<String, Any?>()
    }

    // 当前关联的书源
    var source: BookSource? = null

    /**
     * 浏览器打开器接口 - 由 Activity 实现
     * 用于 startBrowserAwait() 打开 WebView 对话框
     */
    interface BrowserOpener {
        /**
         * 在 UI 线程打开 WebView 对话框，用户完成后回调 html 内容
         */
        fun openBrowserForResult(url: String, title: String, callback: (String) -> Unit)
    }

    /** 浏览器打开器（由 LoginHelper 或 LoginActivity 注入） */
    var browserOpener: BrowserOpener? = null

    // ==================== Cookie 管理 ====================
    
    fun getCookie(domain: String): String {
        return CookieStore.getCookie(domain)
    }

    fun getCookie(domain: String, key: String): String {
        val cookie = CookieStore.getCookie(domain)
        if (cookie.isBlank()) return ""
        return cookie.split(";").map { it.trim() }
            .firstOrNull { it.startsWith("$key=") }
            ?.substringAfter("=") ?: ""
    }

    // getKey 是 getCookie 的别名（番茄源使用）
    fun getKey(domain: String, key: String): String = getCookie(domain, key)

    fun setCookie(domain: String, cookie: String) {
        CookieStore.setCookie(domain, cookie)
    }

    fun removeCookie(domain: String) {
        CookieStore.removeCookie(domain)
        Log.d(TAG, "removeCookie: $domain")
    }

    // ==================== 浏览器 ====================

    /**
     * 打开浏览器等待用户操作
     * 对应 java.startBrowserAwait(url, title, showHome)
     * 
     * 使用 CountDownLatch 阻塞当前 Rhino 线程，直到 WebView 对话框关闭
     */
    fun startBrowserAwait(url: String, title: String = "", showHome: Boolean = false): Any? {
        Log.i(TAG, "startBrowserAwait: url=${url.take(200)}, title=$title")
        val opener = browserOpener
        if (opener == null) {
            Log.w(TAG, "browserOpener 未设置，返回空结果")
            return BrowserResult("")
        }
        val latch = CountDownLatch(1)
        var resultHtml = ""
        try {
            opener.openBrowserForResult(url, title) { html ->
                resultHtml = html
                latch.countDown()
            }
            // 阻塞等待用户完成操作（最多 5 分钟）
            latch.await(5, TimeUnit.MINUTES)
        } catch (e: Exception) {
            Log.e(TAG, "startBrowserAwait 异常: ${e.message}", e)
            latch.countDown()
        }
        Log.d(TAG, "startBrowserAwait 返回: ${resultHtml.take(200)}")
        return BrowserResult(resultHtml)
    }

    fun startBrowserAwait(url: String, title: String): Any? {
        return startBrowserAwait(url, title, false)
    }

    fun startBrowser(url: String, title: String = ""): String {
        Log.i(TAG, "startBrowser: $url ($title)")
        return url
    }

    fun showBrowser(url: String, html: String? = null): String {
        Log.i(TAG, "showBrowser: $url")
        return url
    }

    // ==================== 变量管理 ====================

    fun getVariable(): String {
        val v = source?.getVariable() ?: ""
        if (v.isBlank()) {
            return try {
                val map = variables.filterKeys { it.startsWith("var_") }
                if (map.isEmpty()) "{}"
                else com.google.gson.Gson().toJson(map.mapKeys { it.key.removePrefix("var_") })
            } catch (_: Exception) { "{}" }
        }
        return v
    }

    fun setVariable(value: String?) {
        source?.putVariable(value)
        Log.d(TAG, "setVariable: ${value?.take(100)}")
    }

    // ==================== 变量存取 (java.get/java.put) ====================

    fun get(key: String): Any? {
        return variables[key]
    }

    fun put(key: String, value: Any?): Any? {
        variables[key] = value
        Log.d(TAG, "put($key, ${value?.toString()?.take(100)})")
        return value
    }

    // ==================== 登录信息 ====================

    fun getLoginInfoMap(): Map<String, String>? {
        val info = source?.getLoginInfo() ?: return null
        return try {
            @Suppress("UNCHECKED_CAST")
            com.google.gson.Gson().fromJson(info, Map::class.java) as? Map<String, String>
        } catch (_: Exception) { null }
    }

    fun upLoginData(data: Map<String, Any?>?) {
        callback?.onUiUpdate(data)
    }

    @JvmOverloads
    fun reLoginView(deltaUp: Boolean = false) {
        callback?.onUiRefresh()
    }

    fun refreshExplore() {
        callback?.onUiRefresh()
    }

    // ==================== HTTP ====================

    fun ajax(url: String): String {
        // 处理 "url, {options}" 格式
        if (url.contains(", {") || url.contains(",{")) {
            val commaIdx = url.indexOf(", {").let { if (it < 0) url.indexOf(",{") else it }
            if (commaIdx > 0) {
                val realUrl = url.substring(0, commaIdx).trim()
                val optionsPart = url.substring(commaIdx + 1).trim()
                return ajaxWithOptions(realUrl, optionsPart)
            }
        }
        return try {
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { it.body?.string() ?: "" }
        } catch (e: Exception) {
            Log.e(TAG, "ajax error: ${e.message}")
            ""
        }
    }

    fun ajax(url: String, options: String): String = ajaxWithOptions(url, options)

    private fun ajaxWithOptions(url: String, options: String): String {
        return try {
            val opts = try {
                @Suppress("UNCHECKED_CAST")
                com.google.gson.Gson().fromJson(options, Map::class.java) as? Map<String, Any?>
            } catch (_: Exception) { null }

            val method = (opts?.get("method")?.toString() ?: "GET").uppercase()
            val headers = LinkedHashMap<String, String>()
            @Suppress("UNCHECKED_CAST")
            val headerMap = opts?.get("headers") as? Map<String, Any?>
            headerMap?.forEach { (k, v) -> headers[k] = v.toString() }

            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val reqBuilder = Request.Builder().url(url)
            headers.forEach { (k, v) -> reqBuilder.addHeader(k, v) }

            when (method) {
                "POST" -> {
                    val bodyStr = opts?.get("body")?.toString() ?: "{}"
                    val mediaType = "application/json; charset=utf-8".toMediaType()
                    reqBuilder.post(bodyStr.toRequestBody(mediaType))
                }
            }
            client.newCall(reqBuilder.build()).execute().use { it.body?.string() ?: "" }
        } catch (e: Exception) {
            Log.e(TAG, "ajaxWithOptions error: ${e.message}")
            ""
        }
    }

    fun request(url: String, method: String = "GET", body: Any? = null): String {
        return try {
            val bodyStr = when (body) {
                is String -> body
                is Map<*, *> -> com.google.gson.Gson().toJson(body)
                else -> "{}"
            }
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val reqBuilder = Request.Builder().url(url)
            when (method.uppercase()) {
                "POST" -> {
                    val mediaType = "application/json; charset=utf-8".toMediaType()
                    reqBuilder.post(bodyStr.toRequestBody(mediaType))
                }
            }
            client.newCall(reqBuilder.build()).execute().use { it.body?.string() ?: "" }
        } catch (e: Exception) {
            Log.e(TAG, "request error: ${e.message}")
            ""
        }
    }

    // ==================== 编码 ====================

    fun base64Encode(str: String): String =
        try { android.util.Base64.encodeToString(str.toByteArray(), android.util.Base64.NO_WRAP) } catch (_: Exception) { "" }

    fun base64Decode(str: String): String =
        try { String(android.util.Base64.decode(str, android.util.Base64.NO_WRAP)) } catch (_: Exception) { "" }

    fun hexDecodeToString(hex: String): String {
        return try {
            if (hex.trimStart().let { it.startsWith("{") || it.startsWith("[") }) hex
            else {
                val bytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                String(bytes, Charsets.UTF_8)
            }
        } catch (_: Exception) { hex }
    }

    fun parseJson(str: String): Any? {
        return try { com.google.gson.Gson().fromJson(str, Any::class.java) } catch (_: Exception) { null }
    }

    fun parseJsonSafely(str: String): Any? = parseJson(str)

    // ==================== 工具 ====================

    fun copyText(text: String) { Log.i(TAG, "copy: $text") }
    fun log(msg: String) { Log.i(TAG, msg) }
    fun toast(msg: String) { Log.i(TAG, "Toast: $msg") }
    fun longToast(msg: String) { Log.i(TAG, "LongToast: $msg") }
    fun getString(key: String): String = get(key)?.toString() ?: ""

    fun startBrowserDp(url: String, title: String = ""): String = startBrowser(url, title)

    /**
     * 模拟浏览器返回结果 - 支持 body() 方法调用
     * 书源 JS 中通过 java.startBrowserAwait().body() 获取 HTML
     */
    class BrowserResult(val html: String) {
        fun body(): String = html
        override fun toString(): String = html
    }
}
