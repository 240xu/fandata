package io.legado.engine.entity
data class Book(
    var bookUrl: String = "", var tocUrl: String = "", var origin: String = "",
    var originName: String = "", var originOrder: Int = 0, var name: String = "",
    var author: String = "", var kind: String? = null, var coverUrl: String? = null,
    var intro: String? = null, var wordCount: String? = null,
    var latestChapterTitle: String? = null, var variable: String? = null,
    var type: Int = 0, var infoHtml: String? = null, var tocHtml: String? = null,
    var totalChapterNum: Int = 0, var durChapterIndex: Int = 0
) {
    fun toSearchBook() = SearchBook(bookUrl=bookUrl, origin=origin, originName=originName, type=type,
        name=name, author=author, kind=kind, coverUrl=coverUrl, intro=intro,
        wordCount=wordCount, latestChapterTitle=latestChapterTitle, tocUrl=tocUrl, variable=variable, originOrder=originOrder)
}