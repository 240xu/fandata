package io.legado.engine.analyze

import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.Option

class AnalyzeByJSonPath(private var json: Any?) {
    companion object {
        private val conf = Configuration.defaultConfiguration()
            .addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL, Option.SUPPRESS_EXCEPTIONS)
    }

    fun getStringList(rule: String): List<String> {
        if (json == null) return emptyList()
        return try {
            val jsonStr = when (val j = json) {
                is String -> j
                else -> j.toString()
            }
            JsonPath.using(conf).parse(jsonStr).read<List<Any>>(rule).map { it.toString() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getString(rule: String): String = getStringList(rule).firstOrNull() ?: ""

    fun getObject(rule: String): Any? {
        if (json == null) return null
        return try {
            val jsonStr = when (val j = json) {
                is String -> j
                else -> j.toString()
            }
            JsonPath.using(conf).parse(jsonStr).read(rule)
        } catch (_: Exception) {
            null
        }
    }
}