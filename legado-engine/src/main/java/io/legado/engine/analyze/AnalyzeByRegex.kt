package io.legado.engine.analyze

class AnalyzeByRegex {
    companion object {
        fun getStringList(content: String, rule: String): List<String> {
            return try {
                Regex(rule).findAll(content).map { it.value }.toList()
            } catch (_: Exception) {
                emptyList()
            }
        }

        fun getString(content: String, rule: String): String {
            return getStringList(content, rule).firstOrNull() ?: ""
        }

        fun replace(content: String, rule: String, replacement: String): String {
            return try {
                content.replace(Regex(rule), replacement)
            } catch (_: Exception) {
                content
            }
        }
    }
}