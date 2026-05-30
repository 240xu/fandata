package com.fandata.plugin

import android.content.Context
import android.util.Log
import io.legado.engine.entity.BookSource

/**
 * 书源管理器 - 从 JSON 加载/管理 Legado 书源
 */
object BookSourceManager {
    private const val TAG = "BookSourceManager"
    private val sources = mutableListOf<BookSource>()
    private var currentSource: BookSource? = null

    fun getAll(): List<BookSource> = sources.toList()
    fun getCurrent(): BookSource? = currentSource
    fun setCurrent(source: BookSource?) { currentSource = source }

    /**
     * 从 JSON 字符串导入书源
     */
    fun importFromJson(json: String, clearExisting: Boolean = true): Int {
        val newSources = BookSource.fromJsonArray(json)
        val count = newSources.size
        if (clearExisting) sources.clear()
        val enabledSources = newSources.filter { it.enabled }
        // 去重：按 bookSourceUrl
        for (src in enabledSources) {
            sources.removeAll { it.bookSourceUrl == src.bookSourceUrl }
            sources.add(src)
        }
        Log.i(TAG, "导入 $count 个书源，当前共 ${sources.size} 个启用书源")
        // 保存到共享存储，供 LoginActivity 跨进程访问
        try {
            val ctx = Class.forName("android.app.ActivityThread")
                .getMethod("currentApplication").invoke(null) as? android.content.Context
            ctx?.let { SharedSourceStorage.saveSources(it, com.google.gson.Gson().toJson(sources)) }
        } catch (_: Exception) {}
        if (currentSource == null && sources.isNotEmpty()) {
            currentSource = sources.first()
        }
        return sources.size
    }

    /**
     * 从 assets 加载默认书源
     * 使用 createPackageContext 获取插件自身的 Context
     */
    fun loadFromAssets(context: Context) {
        Log.i(TAG, "loadFromAssets 开始, context=${context.javaClass.simpleName}")
        try {
            val pluginContext = try {
                context.createPackageContext(
                    "com.fandata.plugin",
                    Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY
                )
            } catch (e: Exception) {
                Log.w(TAG, "createPackageContext 失败，使用原始 context: ${e.message}")
                context
            }
            // 加载内置书源
            try {
                val json = pluginContext.assets.open("book_source.json").bufferedReader().use { it.readText() }
                importFromJson(json)
                Log.i(TAG, "book_source.json 加载完成, 当前 ${sources.size} 个源")
            } catch (e: Exception) {
                Log.w(TAG, "加载 book_source.json 失败: ${e.message}")
            }
            // 加载番茄小说源（追加模式，不清除已有源）
            try {
                val fanqieJson = pluginContext.assets.open("fanqie_source.json").bufferedReader().use { it.readText() }
                importFromJson(fanqieJson, clearExisting = false)
                Log.i(TAG, "番茄源加载完成")
            } catch (e: Exception) {
                Log.e(TAG, "加载 fanqie_source.json 失败: ${e.message}", e)
            }
        } catch (e: Exception) {
            Log.w(TAG, "加载默认书源失败: ${e.message}")
        }
    }

    /**
     * 从文件路径导入
     */
    fun importFromFile(path: String): Int {
        return try {
            val json = java.io.File(path).readText()
            importFromJson(json)
        } catch (e: Exception) {
            Log.e(TAG, "文件导入失败: ${e.message}")
            0
        }
    }

    /**
     * 添加单个书源
     */
    fun addSource(source: BookSource) {
        sources.removeAll { it.bookSourceUrl == source.bookSourceUrl }
        sources.add(source)
    }

    /**
     * 移除书源
     */
    fun removeSource(sourceUrl: String) {
        sources.removeAll { it.bookSourceUrl == sourceUrl }
        if (currentSource?.bookSourceUrl == sourceUrl) {
            currentSource = sources.firstOrNull()
        }
    }
}