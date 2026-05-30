package com.fandata.plugin.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.net.http.SslError
import android.os.Bundle
import android.view.View
import android.webkit.*
import android.widget.*
import com.fandata.plugin.BookSourceManager
import com.fandata.plugin.login.CookieStore
import com.fandata.plugin.login.LoginHelper
import com.fandata.plugin.login.LoginJsExtensions
import com.fandata.plugin.login.RowUi
import java.util.concurrent.CountDownLatch

/**
 * 登录 Activity - 支持 WebView 模式和自定义 UI 模式
 * 
 * 自定义 UI 模式下，按钮的 action 字段决定调用哪个 JS 函数：
 * - action = "fq_login()" -> 调用 fq_login()
 * - action = "SortFilter()" -> 调用 SortFilter()（内部调用 startBrowserAwait）
 * - action = null -> 调用默认 login()
 * 
 * 实现 BrowserOpener 接口，为 LoginJsExtensions 提供 WebView 浏览器功能
 */
class LoginActivity : Activity(), LoginHelper.LoginCallback, LoginJsExtensions.BrowserOpener {

    private var webView: WebView? = null
    private var scrollView: ScrollView? = null
    private var formContainer: LinearLayout? = null
    private val formData = mutableMapOf<String, String>()
    private var currentSource: io.legado.engine.entity.BookSource? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CookieStore.init(this)

        // 如果 BookSourceManager 为空（跨进程），从共享存储加载
        if (BookSourceManager.getAll().isEmpty()) {
            try {
                val json = com.fandata.plugin.SharedSourceStorage.loadSources(this)
                if (json != null) {
                    BookSourceManager.importFromJson(json)
                }
            } catch (e: Exception) {
                android.util.Log.w("LoginActivity", "从共享存储加载书源失败: ${e.message}")
            }
        }

        val sourceUrl = intent.getStringExtra("sourceUrl") ?: ""
        currentSource = BookSourceManager.getAll().find { it.bookSourceUrl == sourceUrl }
            ?: BookSourceManager.getCurrent()

        val source = currentSource
        if (source == null) {
            Toast.makeText(this, "未找到书源", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        when (LoginHelper.getLoginMode(source)) {
            LoginHelper.LoginMode.WebView -> setupWebViewLogin(source)
            LoginHelper.LoginMode.CustomUI -> setupCustomUiLogin(source)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebViewLogin(source: io.legado.engine.entity.BookSource) {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 16, 16, 16)
        }
        val titleText = TextView(this).apply {
            text = "登录 - ${source.bookSourceName}"
            textSize = 18f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val doneBtn = Button(this).apply {
            text = "完成"
            setOnClickListener { checkAndSaveCookies(source) }
        }
        toolbar.addView(titleText)
        toolbar.addView(doneBtn)
        layout.addView(toolbar)

        val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 8)
        }
        layout.addView(progressBar)

        val wv = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.userAgentString = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36"

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    url?.let { CookieStore.setCookieByUrl(it, CookieManager.getInstance().getCookie(it)) }
                }
                override fun onPageFinished(view: WebView?, url: String?) {
                    url?.let { CookieStore.setCookieByUrl(it, CookieManager.getInstance().getCookie(it)) }
                    progressBar.progress = 100
                }
                override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                    handler?.proceed()
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progressBar.progress = newProgress
                }
            }
        }
        webView = wv
        layout.addView(wv)
        setContentView(layout)

        val loginUrl = LoginHelper.getWebViewLoginUrl(source)
        if (loginUrl != null) {
            val headers = source.getHeaderMap()
            wv.loadUrl(loginUrl, headers)
        } else {
            Toast.makeText(this, "登录 URL 为空", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupCustomUiLogin(source: io.legado.engine.entity.BookSource) {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // 工具栏
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 16, 16, 16)
        }
        val titleText = TextView(this).apply {
            text = "登录 - ${source.bookSourceName}"
            textSize = 18f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val doneBtn = Button(this).apply {
            text = "完成"
            setOnClickListener { checkAndSaveCookies(source) }
        }
        toolbar.addView(titleText)
        toolbar.addView(doneBtn)
        layout.addView(toolbar)

        scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        formContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 16)
        }
        scrollView!!.addView(formContainer)
        layout.addView(scrollView)
        setContentView(layout)

        val rows = LoginHelper.getLoginUiRows(source)
        buildFormUi(rows, source)
    }

    /**
     * 根据 loginUi 定义构建表单
     */
    private fun buildFormUi(rows: List<RowUi>, source: io.legado.engine.entity.BookSource) {
        formContainer?.removeAllViews()

        for (row in rows) {
            when (row.type) {
                RowUi.Type.text, RowUi.Type.password -> {
                    val label = TextView(this).apply {
                        text = row.name
                        textSize = 14f
                        setPadding(0, 8, 0, 4)
                    }
                    formContainer?.addView(label)

                    val editText = EditText(this).apply {
                        tag = row.name
                        hint = row.default ?: ""
                        if (row.type == RowUi.Type.password) {
                            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                        }
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                            setMargins(0, 0, 0, 16)
                        }
                    }
                    editText.setOnTextChangedListener { text ->
                        formData[row.name] = text?.toString() ?: ""
                    }
                    formData[row.name]?.let { editText.setText(it) }
                    formContainer?.addView(editText)
                }

                RowUi.Type.button -> {
                    val button = Button(this).apply {
                        text = row.name
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                            setMargins(0, 8, 0, 8)
                        }
                        setOnClickListener {
                            val action = row.action
                            if (action != null && action.isNotBlank()) {
                                // 有 action：执行指定的 JS 函数
                                LoginHelper.executeAction(source, formData.toMap(), action, this@LoginActivity, this@LoginActivity)
                            } else {
                                // 无 action：执行默认 login()
                                LoginHelper.executeLogin(source, formData.toMap(), this@LoginActivity)
                            }
                        }
                    }
                    formContainer?.addView(button)
                }

                RowUi.Type.toggle -> {
                    val switchRow = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                            setMargins(0, 8, 0, 8)
                        }
                    }
                    val label = TextView(this).apply {
                        text = row.name
                        textSize = 14f
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    val switch = Switch(this).apply {
                        tag = row.name
                        isChecked = formData[row.name]?.toBoolean() ?: (row.default?.toBoolean() ?: false)
                        setOnCheckedChangeListener { _, isChecked ->
                            formData[row.name] = isChecked.toString()
                        }
                    }
                    switchRow.addView(label)
                    switchRow.addView(switch)
                    formContainer?.addView(switchRow)
                }

                RowUi.Type.select -> {
                    val label = TextView(this).apply {
                        text = row.name
                        textSize = 14f
                        setPadding(0, 8, 0, 4)
                    }
                    formContainer?.addView(label)

                    val spinner = Spinner(this).apply {
                        tag = row.name
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                            setMargins(0, 0, 0, 16)
                        }
                    }
                    row.chars?.let { options ->
                        val adapter = ArrayAdapter(this@LoginActivity, android.R.layout.simple_spinner_item, options.filterNotNull())
                        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        spinner.adapter = adapter
                        formData[row.name]?.let { current ->
                            val idx = options.indexOf(current)
                            if (idx >= 0) spinner.setSelection(idx)
                        }
                    }
                    spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                            formData[row.name] = parent?.getItemAtPosition(position)?.toString() ?: ""
                        }
                        override fun onNothingSelected(parent: AdapterView<*>?) {}
                    }
                    formContainer?.addView(spinner)
                }
            }
        }
    }

    private fun checkAndSaveCookies(source: io.legado.engine.entity.BookSource) {
        val url = source.bookSourceUrl
        val cookie = CookieManager.getInstance().getCookie(url)
        if (cookie != null && cookie.isNotBlank()) {
            CookieStore.setCookieByUrl(url, cookie)
            Toast.makeText(this, "Cookie 已保存", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "未检测到登录状态", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    override fun onSuccess() {
        runOnUiThread {
            Toast.makeText(this, "操作成功", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onError(message: String) {
        runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    }

    override fun onUiUpdate(data: Map<String, Any?>?) {
        runOnUiThread {
            data?.forEach { (key, value) ->
                formData[key] = value?.toString() ?: ""
                findViewByTag<EditText>(key)?.setText(formData[key])
                findViewByTag<Switch>(key)?.isChecked = formData[key].toBoolean()
            }
        }
    }

    override fun onUiRefresh() {
        runOnUiThread {
            val source = currentSource ?: return@runOnUiThread
            val rows = LoginHelper.getLoginUiRows(source)
            buildFormUi(rows, source)
        }
    }

    /**
     * BrowserOpener 实现 - 打开 WebView 对话框
     * 供 LoginJsExtensions.startBrowserAwait() 调用
     * 
     * 使用 CountDownLatch 确保在 UI 线程上打开并等待用户操作完成
     */
    @SuppressLint("SetJavaScriptEnabled")
    override fun openBrowserForResult(url: String, title: String, callback: (String) -> Unit) {
        runOnUiThread {
            val dialog = android.app.AlertDialog.Builder(this)
                .setTitle(title.ifBlank { "浏览器" })
                .create()

            val wv = WebView(this).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.userAgentString = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                webViewClient = object : WebViewClient() {
                    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                        handler?.proceed()
                    }
                    override fun onPageFinished(view: WebView?, pageUrl: String?) {
                        // 注入 Cookie 到 CookieStore
                        pageUrl?.let {
                            val cookie = CookieManager.getInstance().getCookie(it)
                            if (cookie != null) CookieStore.setCookieByUrl(it, cookie)
                        }
                    }
                }
            }

            // 处理 data: URL 和普通 URL
            wv.loadUrl(url)

            dialog.setView(wv)
            dialog.setButton(android.app.AlertDialog.BUTTON_POSITIVE, "完成") { _, _ ->
                // 获取 WebView 中的 HTML 内容
                wv.evaluateJavascript(
                    "(function() { try { return document.documentElement.outerHTML; } catch(e) { return '<error>' + e.message; } })()"
                ) { html ->
                    val cleanHtml = html
                        ?.removeSurrounding("\"")
                        ?.replace("\\u003C", "<")
                        ?.replace("\\u003E", ">")
                        ?.replace("\\u0026", "&")
                        ?.replace("\\\"", "\"")
                        ?.replace("\\n", "\n")
                        ?.replace("\\t", "\t")
                        ?: ""
                    callback(cleanHtml)
                }
            }
            dialog.setButton(android.app.AlertDialog.BUTTON_NEGATIVE, "取消") { _, _ ->
                callback("")
            }
            dialog.setOnCancelListener {
                callback("")
            }
            dialog.show()

            // 设置对话框大小
            dialog.window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.95).toInt(),
                (resources.displayMetrics.heightPixels * 0.85).toInt()
            )
        }
    }

    private inline fun <reified T : View> findViewByTag(tag: String): T? {
        return formContainer?.findViewWithTag<T>(tag)
    }

    override fun onDestroy() {
        webView?.destroy()
        super.onDestroy()
    }
}

private fun EditText.setOnTextChangedListener(listener: (CharSequence?) -> Unit) {
    addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { listener(s) }
        override fun afterTextChanged(s: android.text.Editable?) {}
    })
}
