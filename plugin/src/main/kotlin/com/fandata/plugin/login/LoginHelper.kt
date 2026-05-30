package com.fandata.plugin.login

import android.content.Context
import android.util.Log
import io.legado.engine.entity.BookSource
import io.legado.engine.js.JsExtensions
import io.legado.engine.js.RhinoScriptEngine

/**
 * 登录逻辑核心 - 移植自 Legado SourceLoginViewModel + SourceLoginJsExtensions
 * 
 * 支持两种登录模式：
 * 1. WebView 模式：loginUi 为空，直接加载 loginUrl 到 WebView
 * 2. 自定义 UI 模式：loginUi 定义表单，loginUrl 是登录 JS
 */
object LoginHelper {
    private const val TAG = "LoginHelper"
    private var jsEngine: RhinoScriptEngine? = null

    /**
     * 判断登录模式
     */
    fun getLoginMode(source: BookSource): LoginMode {
        val loginUi = source.loginUi
        if (loginUi.isNullOrBlank()) return LoginMode.WebView
        return LoginMode.CustomUI
    }

    /**
     * 获取 WebView 登录 URL
     */
    fun getWebViewLoginUrl(source: BookSource): String? {
        val loginUrl = source.loginUrl ?: return null
        return when {
            loginUrl.startsWith("http") -> loginUrl
            loginUrl.startsWith("@js:") -> null // JS 模式，不是 URL
            loginUrl.startsWith("<js>") -> null
            else -> {
                // 尝试作为相对 URL
                if (source.bookSourceUrl.startsWith("http")) {
                    "${source.bookSourceUrl.trimEnd('/')}/$loginUrl"
                } else null
            }
        }
    }

    /**
     * 获取自定义 UI 表单定义
     */
    fun getLoginUiRows(source: BookSource): List<RowUi> {
        val loginUi = source.loginUi ?: return emptyList()
        return when {
            loginUi.startsWith("@js:") -> {
                val js = loginUi.substring(4)
                val result = evalUiJs(js, source)
                if (result != null) RowUi.fromJsonArray(result) else emptyList()
            }
            loginUi.startsWith("<js>") -> {
                val js = loginUi.substring(4, loginUi.lastIndexOf("<"))
                val result = evalUiJs(js, source)
                if (result != null) RowUi.fromJsonArray(result) else emptyList()
            }
            else -> RowUi.fromJsonArray(loginUi)
        }
    }

    /**
     * 执行登录 JS（自定义 UI 模式的登录按钮点击）
     */
    fun executeLogin(source: BookSource, loginData: Map<String, String>, callback: LoginCallback) {
        val loginJs = source.loginUrl ?: return
        try {
            val engine = getJsEngine()
            val jsExtensions = LoginJsExtensions(callback)
            engine.put("java", jsExtensions)
            engine.put("result", loginData)
            engine.put("book", null)
            engine.put("chapter", null)
            engine.put("isLongClick", false)

            // 执行登录 JS
            val fullJs = """
                $loginJs
                if (typeof login == 'function') { login.apply(this); } else { throw('login function not found'); }
            """.trimIndent()
            engine.evaluate(fullJs)
            callback.onSuccess()
        } catch (e: Exception) {
            Log.e(TAG, "登录执行失败: ${e.message}")
            callback.onError("登录失败: ${e.message}")
        }
    }

    /**
     * 执行 loginCheckJs 检查登录状态
     */
    fun checkLogin(source: BookSource): Boolean {
        val checkJs = source.loginCheckJs ?: return false
        return try {
            val engine = getJsEngine()
            val result = engine.evaluate(checkJs)
            result == true || result?.toString() == "true"
        } catch (_: Exception) { false }
    }

    private fun evalUiJs(js: String, source: BookSource): String? {
        return try {
            val engine = getJsEngine()
            engine.put("java", LoginJsExtensions(null))
            engine.put("src", source)
            engine.evaluate(js)?.toString()
        } catch (_: Exception) { null }
    }

    private fun getJsEngine(): RhinoScriptEngine = jsEngine ?: RhinoScriptEngine().also { jsEngine = it }

    enum class LoginMode { WebView, CustomUI }

    interface LoginCallback {
        fun onSuccess()
        fun onError(message: String)
        fun onUiUpdate(data: Map<String, Any?>?)
        fun onUiRefresh()
    }
}