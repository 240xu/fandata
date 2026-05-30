package io.legado.engine.analyze

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.select.Elements

class AnalyzeByJSoup(private var doc: Any) {
    companion object {
        @JvmStatic
        fun parse(html: String): Document = Jsoup.parse(html)
    }

    fun getStringList(rule: String): List<String> {
        return getElements(rule).map { el -> getAttribute(el, rule) }
    }

    fun getString(rule: String): String = getStringList(rule).firstOrNull() ?: ""

    private fun getAttribute(el: Element, rule: String): String {
        return when {
            rule.contains("@text", true) -> el.text()
            rule.contains("@src", true) -> el.attr("src")
            rule.contains("@href", true) -> el.attr("href")
            rule.contains("@content", true) -> el.attr("content")
            rule.contains("@html", true) -> el.html()
            rule.contains("@outerHtml", true) -> el.outerHtml()
            rule.contains("@", true) -> {
                val attr = rule.substringAfterLast("@").substringBefore("[").trim()
                if (attr.isNotBlank()) el.attr(attr) else el.text()
            }
            else -> el.text()
        }
    }

    fun getElements(ruleStr: String): Elements {
        val el = when (doc) {
            is Element -> doc as Element
            is Document -> doc as Document
            else -> return Elements()
        }
        return try {
            val p = ruleStr.split("@", limit = 2)
            val selector = p[0].trim()
            if (selector.isBlank()) Elements(el) else el.select(selector)
        } catch (_: Exception) {
            Elements()
        }
    }
}