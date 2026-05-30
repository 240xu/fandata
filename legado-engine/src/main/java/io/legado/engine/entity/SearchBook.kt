package io.legado.engine.entity
data class SearchBook(
    var bookUrl: String = "", var origin: String = "", var originName: String = "",
    var type: Int = 0, var name: String = "", var author: String = "",
    var kind: String? = null, var coverUrl: String? = null, var intro: String? = null,
    var wordCount: String? = null, var latestChapterTitle: String? = null,
    var tocUrl: String = "", var time: Long = System.currentTimeMillis(),
    var variable: String? = null, var originOrder: Int = 0, var infoHtml: String? = null
)