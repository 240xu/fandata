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
    fun importFromJson(json: String): Int {
        val newSources = BookSource.fromJsonArray(json)
        val count = newSources.size
        sources.clear()
        sources.addAll(newSources.filter { it.enabled })
        Log.i(TAG, "导入 $count 个书源，启用 ${sources.size} 个")
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
        try {
            // 获取插件自身的 Context，以便访问插件 APK 中的 assets
            val pluginContext = try {
                context.createPackageContext(
                    "com.fandata.plugin",
                    Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY
                )
            } catch (e: Exception) {
                Log.w(TAG, "createPackageContext 失败，使用原始 context: ${e.message}")
                context
            }
            val json = pluginContext.assets.open("book_source.json").bufferedReader().use { it.readText() }
            importFromJson(json)
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