package com.fandata.plugin.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
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

class LoginActivity : Activity(), LoginHelper.LoginCallback {

    private var webView: WebView? = null
    private var scrollView: ScrollView? = null
    private var formContainer: LinearLayout? = null
    private val formData = mutableMapOf<String, String>()
    private var currentSource: io.legado.engine.entity.BookSource? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CookieStore.init(this)

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
            text = "登录 - "
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

    private fun setupCustomUiLogin(source: io.legado.engine.entity.BookSource) {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 16, 16, 16)
        }
        val titleText = TextView(this).apply {
            text = "登录 - "
            textSize = 18f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val submitBtn = Button(this).apply {
            text = "提交"
            setOnClickListener {
                LoginHelper.executeLogin(source, formData.toMap(), this@LoginActivity)
            }
        }
        toolbar.addView(titleText)
        toolbar.addView(submitBtn)
        layout.addView(toolbar)

        val sv = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val fc = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 16)
        }
        sv.addView(fc)
        scrollView = sv
        formContainer = fc
        layout.addView(sv)
        setContentView(layout)

        val rows = LoginHelper.getLoginUiRows(source)
        buildFormUi(rows, source)
    }

    private fun buildFormUi(rows: List<RowUi>, source: io.legado.engine.entity.BookSource) {
        formContainer?.removeAllViews()
        formData.clear()
        for (row in rows) {
            when (row.type) {
                RowUi.Type.text -> {
                    val label = TextView(this).apply { text = row.name; textSize = 14f }
                    val editText = EditText(this).apply {
                        hint = row.name
                        setText(row.default ?: "")
                        tag = row.name
                    }
                    formData[row.name] = row.default ?: ""
                    editText.setOnTextChangedListener { s -> formData[row.name] = s?.toString() ?: "" }
                    formContainer?.addView(label)
                    formContainer?.addView(editText, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 16) })
                }
                RowUi.Type.password -> {
                    val label = TextView(this).apply { text = row.name; textSize = 14f }
                    val editText = EditText(this).apply {
                        hint = row.name
                        inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                        setText(row.default ?: "")
                        tag = row.name
                    }
                    formData[row.name] = row.default ?: ""
                    editText.setOnTextChangedListener { s -> formData[row.name] = s?.toString() ?: "" }
                    formContainer?.addView(label)
                    formContainer?.addView(editText, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 16) })
                }
                RowUi.Type.button -> {
                    val btn = Button(this).apply {
                        text = row.name
                        setOnClickListener {
                            row.action?.let { action ->
                                try {
                                    LoginHelper.executeLogin(source, formData.toMap(), this@LoginActivity)
                                } catch (_: Exception) {}
                            }
                        }
                    }
                    row.action?.let { action ->
                        btn.setOnLongClickListener {
                            try { LoginHelper.executeLogin(source, formData.toMap(), this@LoginActivity) } catch (_: Exception) {}
                            true
                        }
                    }
                    formContainer?.addView(btn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 16, 0, 16) })
                }
                RowUi.Type.toggle -> {
                    val toggle = Switch(this).apply {
                        text = row.name
                        isChecked = row.default?.toBoolean() ?: false
                        tag = row.name
                    }
                    formData[row.name] = (row.default?.toBoolean() ?: false).toString()
                    toggle.setOnCheckedChangeListener { _, checked -> formData[row.name] = checked.toString() }
                    formContainer?.addView(toggle, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 8, 0, 8) })
                }
                RowUi.Type.select -> {
                    val label = TextView(this).apply { text = row.name; textSize = 14f }
                    val spinner = Spinner(this)
                    val options = row.chars?.filterNotNull()?.toTypedArray() ?: arrayOf()
                    spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, options)
                    formData[row.name] = options.firstOrNull() ?: ""
                    spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                            formData[row.name] = options.getOrElse(position) { "" }
                        }
                        override fun onNothingSelected(parent: AdapterView<*>?) {}
                    }
                    formContainer?.addView(label)
                    formContainer?.addView(spinner, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 24) })
                }
            }
        }
    }

    private fun checkAndSaveCookies(source: io.legado.engine.entity.BookSource) {
        val url = LoginHelper.getWebViewLoginUrl(source) ?: source.bookSourceUrl
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
            Toast.makeText(this, "登录成功", Toast.LENGTH_SHORT).show()
            finish()
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

