package com.fandata.plugin

import android.content.Context
import android.util.Log

/**
 * 书源共享存储 - 跨进程共享书源数据
 * 使用文件存储，确保 LoginActivity 可以在独立进程中访问
 */
object SharedSourceStorage {
    private const val TAG = "SharedSourceStorage"
    private const val FILE_NAME = "fandata_sources.json"
    
    fun saveSources(context: Context, sources: String) {
        try {
            context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE).use {
                it.write(sources.toByteArray())
            }
            Log.d(TAG, "书源已保存到共享存储")
        } catch (e: Exception) {
            Log.e(TAG, "保存书源失败: ${e.message}")
        }
    }
    
    fun loadSources(context: Context): String? {
        return try {
            context.openFileInput(FILE_NAME).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.d(TAG, "读取共享书源失败: ${e.message}")
            null
        }
    }
}
