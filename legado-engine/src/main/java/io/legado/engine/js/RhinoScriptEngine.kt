package io.legado.engine.js

import android.util.Log
import org.mozilla.javascript.ClassShutter
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.ImporterTopLevel
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.Wrapper

/**
 * Rhino JS 引擎 - 移植自 Legado，精简版
 * 支持 ES6、ClassShutter 安全沙箱、JSON 全局对象
 */
class RhinoScriptEngine {
    companion object {
        private const val TAG = "RhinoScriptEngine"
        private var factoryInitialized = false

        fun initFactory() {
            if (factoryInitialized) return
            synchronized(this) {
                if (factoryInitialized) return
                try {
                    ContextFactory.initGlobal(object : ContextFactory() {
                        override fun makeContext(): Context {
                            val cx = super.makeContext()
                            cx.languageVersion = Context.VERSION_ES6
                            cx.setClassShutter(RhinoClassShutter)
                            return cx
                        }
                        override fun hasFeature(cx: Context, featureIndex: Int): Boolean {
                            return when (featureIndex) {
                                Context.FEATURE_ENABLE_JAVA_MAP_ACCESS -> true
                                Context.FEATURE_INTEGER_WITHOUT_DECIMAL_PLACE -> true
                                else -> super.hasFeature(cx, featureIndex)
                            }
                        }
                    })
                    factoryInitialized = true
                    Log.i(TAG, "ContextFactory 初始化完成")
                } catch (e: Exception) {
                    Log.w(TAG, "ContextFactory 初始化失败(可能已初始化): ${e.message}")
                    factoryInitialized = true
                }
            }
        }
    }

    private val scope: ImporterTopLevel

    init {
        initFactory()
        val cx = Context.enter()
        try {
            cx.optimizationLevel = -1
            cx.languageVersion = Context.VERSION_ES6
            scope = ImporterTopLevel(cx)
            try {
                cx.evaluateString(scope, """
                    if (typeof JSON === 'undefined') {
                        var JSON = {
                            parse: function(s) { return org.mozilla.javascript.NativeJSON.parse(null, s, null); },
                            stringify: function(v) { return '' + org.mozilla.javascript.NativeJSON.stringify(null, v, null, null); }
                        };
                    }
                """.trimIndent(), "json_init", 1, null)
            } catch (e: Exception) {
                Log.w(TAG, "JSON polyfill 失败，使用后备: ${e.message}")
                cx.evaluateString(scope, """
                    var JSON = {
                        parse: function(s) {
                            var Gson = Java.type('com.google.gson.Gson');
                            return new Gson().fromJson(s, java.lang.Object.class);
                        },
                        stringify: function(v) {
                            var Gson = Java.type('com.google.gson.Gson');
                            return new Gson().toJson(v);
                        }
                    };
                """.trimIndent(), "json_fallback", 1, null)
            }
        } finally {
            Context.exit()
        }
    }

    fun evaluate(script: String): Any? {
        val cx = Context.enter()
        try {
            cx.optimizationLevel = -1
            cx.languageVersion = Context.VERSION_ES6
            val result = cx.evaluateString(scope, script, "legado_js", 1, null)
            return unwrapResult(result)
        } catch (e: Exception) {
            Log.e(TAG, "evaluate error: ${e.message}", e)
            throw e
        } finally {
            Context.exit()
        }
    }

    fun evaluateAsString(script: String): String {
        val result = evaluate(script)
        return when (result) {
            is String -> result
            is Undefined -> ""
            null -> ""
            else -> Context.toString(result)
        }
    }

    /**
     * 设置全局变量 - 修复：需要 Context.enter() 才能调用 Context.javaToJS
     */
    fun put(key: String, value: Any?) {
        val cx = Context.enter()
        try {
            val jsValue = Context.javaToJS(value, scope)
            ScriptableObject.putProperty(scope, key, jsValue)
        } finally {
            Context.exit()
        }
    }

    fun get(key: String): Any? {
        val cx = Context.enter()
        try {
            val value = ScriptableObject.getProperty(scope, key)
            return unwrapResult(value)
        } finally {
            Context.exit()
        }
    }

    fun putFunction(name: String, func: org.mozilla.javascript.BaseFunction) {
        val cx = Context.enter()
        try {
            ScriptableObject.putProperty(scope, name, func)
        } finally {
            Context.exit()
        }
    }

    private fun unwrapResult(value: Any?): Any? {
        return when (value) {
            is Undefined -> null
            is Wrapper -> unwrapResult(value.unwrap())
            else -> value
        }
    }
}
