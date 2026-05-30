package com.fandata.plugin.login

import android.util.Log
import io.legado.engine.entity.BookSource
import io.legado.engine.js.RhinoScriptEngine

/**
 * 登录逻辑核心 - 移植自 Legado SourceLoginViewModel + SourceLoginJsExtensions
 * 
 * 支持两种登录模式：
 * 1. WebView 模式：loginUi 为空，直接加载 loginUrl 到 WebView
 * 2. 自定义 UI 模式：loginUi 定义表单，loginUrl 是登录 JS
 * 
 * 按钮 action 处理：
 * - 有 action：执行 loginUrl JS 定义所有函数，然后调用 action 指定的函数
 * - 无 action：执行 loginUrl JS，调用默认 login() 函数
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
            loginUrl.startsWith("@js:") -> null
            loginUrl.startsWith("<js>") -> null
            else -> {
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
     * 执行登录 JS（自定义 UI 模式的默认按钮点击）
     * 无 action 时调用 login() 函数
     */
    fun executeLogin(source: BookSource, loginData: Map<String, String>, callback: LoginCallback) {
        executeAction(source, loginData, null, callback)
    }

    /**
     * 执行指定 action 的 JS 函数（带 BrowserOpener 支持）
     * 
     * 流程：
     * 1. 提取 loginUrl 中的 JS 代码
     * 2. 在 Rhino 引擎中执行该 JS（定义所有函数）
     * 3. 如果指定了 action，调用 action 中的函数名；否则调用 login()
     * 
     * 支持的 action 格式：
     * - "fq_login()" -> 调用 fq_login 函数
     * - "logout()" -> 调用 logout 函数
     * - "boy()" -> 调用 boy 函数
     * - "SortFilter()" -> 调用 SortFilter 函数（内部调用 startBrowserAwait）
     * - "java.setVariable('key', 'value')" -> 执行 JS 表达式
     */
    fun executeAction(
        source: BookSource, 
        loginData: Map<String, String>, 
        action: String?, 
        callback: LoginCallback,
        browserOpener: LoginJsExtensions.BrowserOpener? = null
    ) {
        val loginJs = source.loginUrl ?: run {
            callback.onError("书源未定义登录脚本 (loginUrl)")
            return
        }
        try {
            val engine = getJsEngine()
            val jsExtensions = LoginJsExtensions(callback)
            jsExtensions.source = source
            jsExtensions.browserOpener = browserOpener
            engine.put("java", jsExtensions)
            engine.put("cookie", jsExtensions)
            engine.put("source", source)
            engine.put("src", source)
            engine.put("result", loginData)
            engine.put("book", null)
            engine.put("chapter", null)
            engine.put("isLongClick", false)

            // 提取并执行 loginUrl 中的 JS 代码（定义所有函数）
            val extractedJs = source.getLoginJs() ?: loginJs
            Log.d(TAG, "执行 loginUrl JS (${extractedJs.length} chars)")
            engine.evaluate(extractedJs)

            // 加载 jsLib（书源公共函数，如 getServerHost() 等）
            source.jsLib?.let { lib ->
                if (lib.isNotBlank()) {
                    try {
                        engine.evalJsLib(lib)
                        Log.d(TAG, "jsLib 加载成功")
                    } catch (e: Exception) {
                        Log.w(TAG, "jsLib 加载失败: ${e.message}")
                    }
                }
            }

            // 根据 action 调用对应函数
            val callTarget = action?.trim()
            if (!callTarget.isNullOrEmpty()) {
                Log.i(TAG, "调用 action: $callTarget")
                // action 可能是 "funcName()" 或 "java.xxx()" 等表达式
                if (callTarget.matches(Regex("^\\w+\\(\\)$"))) {
                    // 简单函数调用: fq_login() -> fq_login()
                    engine.evaluate(callTarget)
                } else if (callTarget.contains("(")) {
                    // 复杂表达式: java.xxx() 等
                    engine.evaluate(callTarget)
                } else {
                    // 纯函数名: fq_login -> fq_login()
                    engine.evaluate("${callTarget}()")
                }
            } else {
                // 无 action，调用默认 login()
                Log.d(TAG, "调用默认 login() 函数")
                engine.evaluate("if (typeof login === 'function') login(); else if (typeof fq_login === 'function') fq_login();")
            }
            callback.onSuccess()
        } catch (e: Exception) {
            Log.e(TAG, "登录执行失败: ${e.message}", e)
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
            engine.put("cookie", LoginJsExtensions(null))
            engine.put("src", source)
            engine.put("source", source)
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
