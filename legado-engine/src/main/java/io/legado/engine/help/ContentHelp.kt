package io.legado.engine.help

/**
 * 内容辅助 - 移植自 Legado ContentHelp
 * 段落重排、对话分段、引号处理
 */
object ContentHelp {
    fun reSegment(content: String, chapterName: String): String {
        var text = content
        text = text.replace("&quot;".toRegex(), "\u201c")
            .replace("[:\uff1a]['\"\u201c\u201d\u2018\u2019\"]+".toRegex(), "\uff1a\u201c")
            .replace("[\"\u201c\u201d\u2018\u2019\"]+\\s*[\"\u201c\u201d\u2018\u2019\"][\\s\"\u201c\u201d\u2018\u2019\"]*".toRegex(), "\u201d\n\u201c")
        val lines = text.split("\n")
        val sb = StringBuilder()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            if (trimmed.length > MAX_LENGTH) {
                sb.append(wrapLine(trimmed))
            } else {
                sb.append(trimmed)
            }
            sb.append("\n")
        }
        return sb.toString().trim()
    }

    private fun wrapLine(line: String): String {
        if (line.length <= MAX_LENGTH) return line
        val sb = StringBuilder()
        var i = 0
        while (i < line.length) {
            val end = minOf(i + MAX_LENGTH, line.length)
            var breakPoint = -1
            for (j in (end - 1) downTo i) {
                if (MARK_SENTENCES_END.indexOf(line[j]) != -1) { breakPoint = j + 1; break }
                if (MARK_PUNCT.indexOf(line[j]) != -1) { breakPoint = j + 1; break }
            }
            if (breakPoint <= i) breakPoint = end
            if (sb.isNotEmpty()) sb.append("\n")
            sb.append(line, i, breakPoint)
            i = breakPoint
        }
        return sb.toString()
    }

    private const val MAX_LENGTH = 55
    private const val MARK_SENTENCES_END = "\uff1f\u3002\uff01?!~"
    private const val MARK_PUNCT = ".\uff0c\u3001\uff1b\uff1a"
}
