package io.legado.engine.entity
data class BookChapter(
    var url: String = "", var title: String = "", var isVolume: Boolean = false,
    var baseUrl: String = "", var bookUrl: String = "", var index: Int = 0,
    var isVip: Boolean = false, var isPay: Boolean = false, var tag: String? = null,
    var variable: String? = null, var tocUrl: String = ""
) { fun getAbsoluteURL(): String = url }