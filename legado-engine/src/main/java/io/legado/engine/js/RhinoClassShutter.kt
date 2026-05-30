package io.legado.engine.js

import org.mozilla.javascript.ClassShutter

object RhinoClassShutter : ClassShutter {
    private val blocked = setOf(
        "java.lang.Runtime", "java.lang.ProcessBuilder",
        "java.lang.System", "java.lang.Thread",
        "java.lang.ClassLoader", "java.lang.reflect.",
        "java.io.File", "java.io.FileInputStream", "java.io.FileOutputStream"
    )
    private val allowed = setOf(
        "java.lang.", "java.util.", "java.math.",
        "java.text.", "java.net.", "org.json.", "org.jsoup.", "com.google.gson.",
        "io.legado.engine."
    )

    override fun visibleToScripts(className: String): Boolean {
        for (b in blocked) {
            if (className.startsWith(b) || className == b) return false
        }
        for (a in allowed) {
            if (className.startsWith(a)) return true
        }
        return false
    }
}
