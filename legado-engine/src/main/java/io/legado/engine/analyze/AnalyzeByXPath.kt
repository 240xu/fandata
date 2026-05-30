package io.legado.engine.analyze

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class AnalyzeByXPath(private var doc: Any) {
    fun getStringList(rule: String): List<String> {
        return try {
            getElements(rule).map { it.text() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getString(rule: String): String = getStringList(rule).firstOrNull() ?: ""

    private fun getElements(rule: String): List<Element> {
        val el = when (doc) {
            is Element -> doc as Element
            is Document -> doc as Document
            else -> return emptyList()
        }
        return try {
            el.select(xpathToCss(rule)).toList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun xpathToCss(x: String): String {
        return x.replace("//", " ").replace("/", " > ")
            .replace("[contains(@class,", ".").replace(")]", "")
            .replace("[@class=", ".").replace("[@id=", "#")
            .replace("[text()]", "").trim()
    }
}