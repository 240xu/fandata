package com.fandata.plugin.login

import android.util.Log

/**
 * 登录 JS 桥接方法 - 移植自 Legado SourceLoginJsExtensions
 * 供书源 loginUrl 中的 JS 代码调用
 */
class LoginJsExtensions(private val callback: LoginHelper.LoginCallback?) {
    companion object { private const val TAG = "LoginJsExt" }

    /**
     * 更新登录 UI 数据 - 对应 java.upLoginData()
     */
    fun upLoginData(data: Map<String, Any?>?) {
        callback?.onUiUpdate(data)
    }

    /**
     * 刷新登录 UI 视图 - 对应 java.reLoginView()
     */
    @JvmOverloads
    fun reLoginView(deltaUp: Boolean = false) {
        callback?.onUiRefresh()
    }

    /**
     * 刷新发现页
     */
    fun refreshExplore() {
        callback?.onUiRefresh()
    }

    /**
     * 复制文本
     */
    fun copyText(text: String) {
        Log.i(TAG, "复制: $text")
    }

    /**
     * 显示浏览器
     */
    fun showBrowser(url: String, html: String? = null) {
        Log.i(TAG, "打开浏览器: $url")
    }

    /**
     * AJAX 请求
     */
    fun ajax(url: String): String {
        return try {
            val client = okhttp3.OkHttpClient()
            val req = okhttp3.Request.Builder().url(url).build()
            client.newCall(req).execute().use { it.body?.string() ?: "" }
        } catch (_: Exception) { "" }
    }

    fun ajax(url: String, options: String): String = ajax(url)

    /**
     * Base64 编解码
     */
    fun base64Encode(str: String): String =
        try { android.util.Base64.encodeToString(str.toByteArray(), android.util.Base64.NO_WRAP) } catch (_: Exception) { "" }

    fun base64Decode(str: String): String =
        try { String(android.util.Base64.decode(str, android.util.Base64.NO_WRAP)) } catch (_: Exception) { "" }

    fun log(msg: String) { Log.i(TAG, msg) }
    fun toast(msg: String) { Log.i(TAG, "Toast: $msg") }
    fun longToast(msg: String) { Log.i(TAG, "LongToast: $msg") }
    fun getCookie(domain: String, key: String): String = CookieStore.getCookie(domain)
    fun setCookie(domain: String, cookie: String) { CookieStore.setCookie(domain, cookie) }
}