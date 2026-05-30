@file:Suppress("unused")
package com.fandata.plugin

import android.util.Log
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import com.fandata.plugin.login.CookieStore
import com.fandata.plugin.settings.PluginConfigProvider
import com.fandata.plugin.ui.PluginSettingsPage
import io.legado.engine.EngineContext
import io.legado.engine.analyze.AnalyzeUrl
import io.nightfish.lightnovelreader.api.plugin.LightNovelReaderPlugin
import io.nightfish.lightnovelreader.api.plugin.Plugin
import io.nightfish.lightnovelreader.api.userdata.UserDataRepositoryApi

/**
 * FanData 插件入口 - Legado 书源转译引擎
 */
@Plugin(
    version = BuildConfig.VERSION_CODE,
    name = "FanData书源引擎",
    versionName = BuildConfig.VERSION_NAME,
    author = "FanData",
    description = "Legado 书源转译引擎 - 导入任意 Legado JSON 书源即可使用",
    updateUrl = "",
    apiVersion = 3
)
class FanDataPlugin(val userDataRepositoryApi: UserDataRepositoryApi) : LightNovelReaderPlugin {
    override fun onLoad() {
        Log.i("FanDataPlugin", "FanData书源引擎已加载")
        try {
            val ctx = Class.forName("android.app.ActivityThread")
                .getMethod("currentApplication").invoke(null) as? android.content.Context
            if (ctx != null) {
                // 初始化 Cookie 存储
                CookieStore.init(ctx)
                // 初始化 ConfigProvider 并注入引擎
                val configProvider = PluginConfigProvider(userDataRepositoryApi)
                EngineContext.configProvider = configProvider
                // 注入外部 Cookie 到引擎 HttpHelper
                AnalyzeUrl.httpHelper.externalCookieStore = CookieStore.cookieStore
                // 加载书源
                BookSourceManager.loadFromAssets(ctx)
                Log.i("FanDataPlugin", "引擎初始化完成, ${BookSourceManager.getAll().size} 个书源")
            } else {
                Log.e("FanDataPlugin", "无法获取 Application Context")
            }
        } catch (e: Exception) {
            Log.e("FanDataPlugin", "初始化失败: ${e.message}")
        }
    }

    @Composable
    override fun PageContent(paddingValues: PaddingValues) {
        PluginSettingsPage(paddingValues = paddingValues, userDataRepo = userDataRepositoryApi)
    }
}

