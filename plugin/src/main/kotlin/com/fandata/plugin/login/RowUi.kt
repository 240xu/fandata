package com.fandata.plugin.login

/**
 * 登录 UI 表单行定义 - 移植自 Legado RowUi
 * 支持: text, password, button, toggle, select
 */
data class RowUi(
    val name: String = "",
    val type: String = "text",
    val action: String? = null,
    val chars: Array<String?>? = null,
    val default: String? = null,
    var viewName: String? = null
) {
    object Type {
        const val text = "text"
        const val password = "password"
        const val button = "button"
        const val toggle = "toggle"
        const val select = "select"
    }
    companion object {
        fun fromJsonArray(json: String): List<RowUi> {
            return try {
                val arr = com.google.gson.Gson().fromJson(json, Array<RowUi>::class.java)
                arr?.toList() ?: emptyList()
            } catch (_: Exception) { emptyList() }
        }
    }
}