package io.legado.engine.webbook

import io.legado.engine.analyze.AnalyzeRule
import io.legado.engine.entity.*

object BookList {
    fun analyzeBookList(
        bookSource: BookSource,
        ruleData: RuleData,
        body: String?,
        baseUrl: String,
        isSearch: Boolean = true
    ): ArrayList<SearchBook> {
        body ?: throw Exception("响应内容为空")
        val bookList = ArrayList<SearchBook>()
        val analyzeRule = AnalyzeRule(ruleData, bookSource)
        analyzeRule.setContent(body).setBaseUrl(baseUrl)

        val sr = if (isSearch) bookSource.getSearchRule() else null
        val er = if (!isSearch) bookSource.getExploreRule() else null
        val useEr = er != null && !er.bookList.isNullOrBlank()

        val ruleBookList = if (useEr) er!!.bookList else sr?.bookList
        val ruleName = if (useEr) er!!.name else sr?.name
        val ruleAuthor = if (useEr) er!!.author else sr?.author
        val ruleBookUrl = if (useEr) er!!.bookUrl else sr?.bookUrl
        val ruleCoverUrl = if (useEr) er!!.coverUrl else sr?.coverUrl
        val ruleIntro = if (useEr) er!!.intro else sr?.intro
        val ruleKind = if (useEr) er!!.kind else sr?.kind
        val ruleLastChapter = if (useEr) er!!.lastChapter else sr?.lastChapter
        val ruleWordCount = if (useEr) er!!.wordCount else sr?.wordCount

        var rl = ruleBookList ?: ""
        var reverse = false
        if (rl.startsWith("-")) { reverse = true; rl = rl.substring(1) }
        if (rl.startsWith("+")) { rl = rl.substring(1) }

        val collections = analyzeRule.getElements(rl)
        if (collections.isEmpty()) return bookList

        for (item in collections) {
            analyzeRule.setContent(item).setBaseUrl(baseUrl)
            val sb = SearchBook(
                origin = bookSource.bookSourceUrl,
                originName = bookSource.bookSourceName,
                originOrder = bookSource.customOrder
            )
            sb.name = try { analyzeRule.getString(ruleName).trim() } catch (_: Exception) { "" }
            if (sb.name.isNotEmpty()) {
                sb.author = try { analyzeRule.getString(ruleAuthor).trim() } catch (_: Exception) { "" }
                sb.kind = try { analyzeRule.getStringList(ruleKind)?.joinToString(",") } catch (_: Exception) { null }
                sb.wordCount = try { analyzeRule.getString(ruleWordCount) } catch (_: Exception) { null }
                sb.latestChapterTitle = try { analyzeRule.getString(ruleLastChapter) } catch (_: Exception) { null }
                sb.intro = try { analyzeRule.getString(ruleIntro) } catch (_: Exception) { null }
                sb.coverUrl = try { analyzeRule.getString(ruleCoverUrl) } catch (_: Exception) { null }
                sb.bookUrl = try { analyzeRule.getString(ruleBookUrl, isUrl = true) } catch (_: Exception) { "" }
                if (sb.bookUrl.isBlank()) sb.bookUrl = baseUrl
                if (!sb.bookUrl.startsWith("http")) {
                    sb.bookUrl = try { java.net.URL(java.net.URL(baseUrl), sb.bookUrl).toString() } catch (_: Exception) { sb.bookUrl }
                }
                sb.tocUrl = sb.bookUrl
                bookList.add(sb)
            }
        }
        if (reverse) bookList.reverse()
        return bookList
    }
}