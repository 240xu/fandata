package io.legado.engine.analyze

class RuleAnalyzer(private var queue: String, code: Boolean = false) {
    private var pos = 0
    private var start = 0
    private var startX = 0
    private var rule = ArrayList<String>()
    private var step = 0
    var elementsType = ""

    fun trim() {
        if (pos < queue.length && (queue[pos] == '@' || queue[pos] < '!')) {
            pos++
            while (pos < queue.length && (queue[pos] == '@' || queue[pos] < '!')) pos++
            start = pos
            startX = pos
        }
    }

    private fun consumeTo(seq: String): Boolean {
        start = pos
        val offset = queue.indexOf(seq, pos)
        return if (offset != -1) { pos = offset; true } else false
    }

    private fun consumeToAny(vararg seq: String): Boolean {
        var p = pos
        while (p != queue.length) {
            for (s in seq) {
                if (queue.regionMatches(p, s, 0, s.length)) {
                    step = s.length
                    this.pos = p
                    return true
                }
            }
            p++
        }
        return false
    }

    private fun chompCodeBalanced(open: Char, close: Char): Boolean {
        var p = pos
        var depth = 0
        var inSingle = false
        var inDouble = false
        do {
            if (p == queue.length) break
            val c = queue[p++]
            if (c != '\\') {
                if (c == '\'' && !inDouble) inSingle = !inSingle
                else if (c == '"' && !inSingle) inDouble = !inDouble
                if (inSingle || inDouble) continue
                if (c == '[') depth++
                else if (c == ']') depth--
                else if (depth == 0) {
                    if (c == open) { depth++; continue }
                    if (c == close) {
                        depth--
                        if (depth == 0) { pos = p; return true }
                    }
                }
            }
        } while (true)
        return false
    }

    private fun chompRuleBalanced(open: Char, close: Char): Boolean {
        var p = pos
        var depth = 0
        do {
            if (p == queue.length) break
            val c = queue[p++]
            if (c == open) depth++
            else if (c == close) {
                depth--
                if (depth == 0) { pos = p; return true }
            }
        } while (true)
        return false
    }

    fun splitRule(step: Int = 0): ArrayList<String> {
        if (step > 0) this.step = step
        if (rule.isNotEmpty()) return rule
        elementsType = ""
        if (!consumeToAny("||", "&&")) {
            rule.add(queue)
            return rule
        }
        elementsType = queue.substring(pos, pos + this.step)
        val end = pos
        pos = 0
        while (consumeTo(elementsType) && pos < end) {
            rule.add(queue.substring(start, pos))
            pos += this.step
        }
        if (pos > end) {
            startX = start
            splitRule()
        } else {
            rule.add(queue.substring(pos))
        }
        return rule
    }

    fun innerRule(inner: String, startStep: Int = 1, endStep: Int = 1, fr: (String) -> String?): String {
        val st = StringBuilder()
        while (consumeTo(inner)) {
            val pp = pos
            if (chompCodeBalanced('{', '}')) {
                val frv = fr(queue.substring(pp + startStep, pos - endStep))
                if (!frv.isNullOrEmpty()) {
                    st.append(queue.substring(startX, pp) + frv)
                    startX = pos
                    continue
                }
            }
            pos += inner.length
        }
        return if (startX == 0) "" else st.apply { append(queue.substring(startX)) }.toString()
    }

    val chompBalanced = if (code) ::chompCodeBalanced else ::chompRuleBalanced
}