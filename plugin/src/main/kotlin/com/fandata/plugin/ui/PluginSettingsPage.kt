package com.fandata.plugin.ui

import android.annotation.SuppressLint
import android.net.http.SslError
import android.webkit.*
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.fandata.plugin.BookSourceManager
import com.fandata.plugin.login.CookieStore
import com.fandata.plugin.login.LoginHelper
import com.fandata.plugin.login.LoginJsExtensions
import io.nightfish.lightnovelreader.api.ui.components.SettingsClickableEntry
import io.nightfish.lightnovelreader.api.ui.components.SettingsSwitchEntry
import io.nightfish.lightnovelreader.api.userdata.UserDataRepositoryApi

@Composable
fun PluginSettingsPage(paddingValues: PaddingValues, userDataRepo: UserDataRepositoryApi) {
    val context = LocalContext.current
    var showLoginDialog by remember { mutableStateOf(false) }

    Column(
        Modifier
            .padding(paddingValues)
            .verticalScroll(rememberScrollState())
            .clip(RoundedCornerShape(16.dp)),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        val src = BookSourceManager.getCurrent()
        if (src != null) {
            Card(
                Modifier.padding(16.dp).fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("当前书源", style = MaterialTheme.typography.titleSmall)
                    Text(src.bookSourceName, style = MaterialTheme.typography.bodyLarge)
                    Text(src.bookSourceUrl, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("共 ${BookSourceManager.getAll().size} 个书源可用",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("功能开关", modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.titleSmall)
        val pr by userDataRepo.booleanUserData("paragraph_review")
            .getFlowWithDefault(false).collectAsState(false)
        SettingsSwitchEntry(
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer),
            title = "段落评论", description = "在正文中显示段落评论标记",
            checked = pr, booleanUserData = userDataRepo.booleanUserData("paragraph_review")
        )
        val bs by userDataRepo.booleanUserData("bookshelf_sync")
            .getFlowWithDefault(true).collectAsState(true)
        SettingsSwitchEntry(
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer),
            title = "书架同步", description = "自动同步阅读进度到服务器",
            checked = bs, booleanUserData = userDataRepo.booleanUserData("bookshelf_sync")
        )
        val rt by userDataRepo.booleanUserData("reading_toast")
            .getFlowWithDefault(false).collectAsState(false)
        SettingsSwitchEntry(
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer),
            title = "阅读提示", description = "显示操作的 Toast 提示",
            checked = rt, booleanUserData = userDataRepo.booleanUserData("reading_toast")
        )
        val ss by userDataRepo.booleanUserData("show_source")
            .getFlowWithDefault(false).collectAsState(false)
        SettingsSwitchEntry(
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer),
            title = "显示来源", description = "在目录中显示章节来源",
            checked = ss, booleanUserData = userDataRepo.booleanUserData("show_source")
        )
        Spacer(Modifier.height(8.dp))
        Text("账号与操作", modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.titleSmall)
        SettingsClickableEntry(
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer),
            title = "登录书源账号",
            description = if (src != null) "登录 ${src.bookSourceName}" else "请先加载书源"
        ) {
            CookieStore.init(context)
            showLoginDialog = true
        }
        SettingsClickableEntry(
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer),
            title = "导入书源",
            description = "从 JSON 文件导入 Legado 书源"
        ) {
            Toast.makeText(context, "将书源 JSON 放到 /Documents/fandata/ 目录后重启插件",
                Toast.LENGTH_LONG).show()
        }
        SettingsClickableEntry(
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer),
            title = "重置设置", description = "恢复所有功能开关为默认值"
        ) {
            userDataRepo.booleanUserData("paragraph_review").set(false)
            userDataRepo.booleanUserData("bookshelf_sync").set(true)
            userDataRepo.booleanUserData("reading_toast").set(false)
            userDataRepo.booleanUserData("show_source").set(false)
            Toast.makeText(context, "设置已重置", Toast.LENGTH_SHORT).show()
        }
    }

    if (showLoginDialog) {
        val src = BookSourceManager.getCurrent()
        if (src != null) {
            LoginDialog(source = src, onDismiss = { showLoginDialog = false })
        }
    }
}

/**
 * 登录对话框 - 支持 WebView 模式和自定义 UI 模式
 * 自定义 UI 模式下，按钮的 action 字段决定调用哪个 JS 函数
 * BrowserOpener 嵌套 WebView 弹窗支持 startBrowserAwait()
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginDialog(
    source: io.legado.engine.entity.BookSource,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var statusMessage by remember { mutableStateOf("") }
    val loginMode = remember { LoginHelper.getLoginMode(source) }

    // 嵌套 WebView 弹窗状态
    var showBrowserDialog by remember { mutableStateOf(false) }
    var browserUrl by remember { mutableStateOf("") }
    var browserTitle by remember { mutableStateOf("") }
    val browserCallbackRef = remember { mutableStateOf<((String) -> Unit)?>(null) }

    // BrowserOpener 实现 - 打开嵌套 WebView 弹窗
    val browserOpener = remember {
        object : LoginJsExtensions.BrowserOpener {
            override fun openBrowserForResult(url: String, title: String, callback: (String) -> Unit) {
                browserUrl = url
                browserTitle = title
                browserCallbackRef.value = callback
                showBrowserDialog = true
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("登录 - ${source.bookSourceName}") },
        text = {
            Column {
                when (loginMode) {
                    LoginHelper.LoginMode.WebView -> {
                        val loginUrl = remember { LoginHelper.getWebViewLoginUrl(source) }
                        if (loginUrl != null) {
                            AndroidView(
                                factory = { ctx ->
                                    WebView(ctx).apply {
                                        settings.javaScriptEnabled = true
                                        settings.domStorageEnabled = true
                                        settings.userAgentString = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36"
                                        webViewClient = object : WebViewClient() {
                                            override fun onPageFinished(view: WebView?, url: String?) {
                                                url?.let {
                                                    val cookie = CookieManager.getInstance().getCookie(it)
                                                    if (cookie != null) CookieStore.setCookieByUrl(it, cookie)
                                                }
                                            }
                                            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                                                handler?.proceed()
                                            }
                                        }
                                        loadUrl(loginUrl, source.getHeaderMap())
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(400.dp)
                            )
                        } else {
                            Text("登录 URL 为空")
                        }
                    }
                    LoginHelper.LoginMode.CustomUI -> {
                        val rows = remember { LoginHelper.getLoginUiRows(source) }
                        val formData = remember { mutableStateMapOf<String, String>() }

                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (row in rows) {
                                when (row.type) {
                                    "text", "password" -> {
                                        OutlinedTextField(
                                            value = formData[row.name] ?: "",
                                            onValueChange = { formData[row.name] = it },
                                            label = { Text(row.name) },
                                            placeholder = { Text(row.default ?: "") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    "button" -> {
                                        Button(
                                            onClick = {
                                                val action = row.action
                                                if (!action.isNullOrBlank()) {
                                                    LoginHelper.executeAction(
                                                        source, formData.toMap(), action,
                                                        createLoginCallback(context) { statusMessage = it },
                                                        browserOpener
                                                    )
                                                } else {
                                                    LoginHelper.executeLogin(
                                                        source, formData.toMap(),
                                                        createLoginCallback(context) { statusMessage = it }
                                                    )
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(row.name)
                                        }
                                    }
                                    "toggle" -> {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(row.name, modifier = Modifier.weight(1f))
                                            Switch(
                                                checked = formData[row.name]?.toBoolean() ?: false,
                                                onCheckedChange = { formData[row.name] = it.toString() }
                                            )
                                        }
                                    }
                                    "select" -> {
                                        Text(row.name, style = MaterialTheme.typography.bodyMedium)
                                        // Simplified select - show as text buttons
                                        row.chars?.filterNotNull()?.forEach { option ->
                                            TextButton(onClick = { formData[row.name] = option }) {
                                                Text(option,
                                                    color = if (formData[row.name] == option)
                                                        MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.onSurface)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (statusMessage.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(statusMessage, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val url = source.bookSourceUrl
                val cookie = CookieManager.getInstance().getCookie(url)
                if (cookie != null && cookie.isNotBlank()) {
                    CookieStore.setCookieByUrl(url, cookie)
                    Toast.makeText(context, "Cookie 已保存", Toast.LENGTH_SHORT).show()
                }
                onDismiss()
            }) { Text("完成") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )

    // 嵌套 WebView 弹窗 - 供 startBrowserAwait() 使用
    if (showBrowserDialog) {
        BrowserWebViewDialog(
            url = browserUrl,
            title = browserTitle,
            onDismiss = {
                showBrowserDialog = false
                browserCallbackRef.value?.invoke("")
                browserCallbackRef.value = null
            },
            onComplete = { html ->
                showBrowserDialog = false
                browserCallbackRef.value?.invoke(html)
                browserCallbackRef.value = null
            }
        )
    }
}

/**
 * WebView 浏览器弹窗 - 供 startBrowserAwait() 使用
 * 用户在 WebView 中操作，点击"完成"后返回 HTML 内容
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserWebViewDialog(
    url: String,
    title: String,
    onDismiss: () -> Unit,
    onComplete: (String) -> Unit
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title.ifBlank { "浏览器" }) },
        text = {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
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
                                pageUrl?.let {
                                    val cookie = CookieManager.getInstance().getCookie(it)
                                    if (cookie != null) CookieStore.setCookieByUrl(it, cookie)
                                }
                            }
                        }
                        // 处理 data: URL 和普通 URL
                        loadUrl(url)
                        webViewRef = this
                    }
                },
                modifier = Modifier.fillMaxWidth().height(500.dp)
            )
        },
        confirmButton = {
            TextButton(onClick = {
                webViewRef?.evaluateJavascript(
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
                    onComplete(cleanHtml)
                }
            }) { Text("完成") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private fun createLoginCallback(
    context: android.content.Context,
    onStatus: (String) -> Unit
): LoginHelper.LoginCallback {
    return object : LoginHelper.LoginCallback {
        override fun onSuccess() {
            Toast.makeText(context, "操作成功", Toast.LENGTH_SHORT).show()
            onStatus("操作成功")
        }
        override fun onError(message: String) {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            onStatus(message)
        }
        override fun onUiUpdate(data: Map<String, Any?>?) {}
        override fun onUiRefresh() {}
    }
}
