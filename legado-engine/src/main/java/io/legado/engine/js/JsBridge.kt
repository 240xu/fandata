package io.legado.engine.js

import android.util.Log
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable

/**
 * JS 全局函数桥接器
 * 将 JsExtensions 的方法注册为 JS 全局函数
 * 
 * 书源 JS 中常用的全局函数：
 * - getVariable(key) / setVariable(key, value) - 变量管理
 * - BaseUrl() - 获取书源 URL
 * - checkEnv() - 检查运行环境
 * - getCloudSettings(force) - 获取云端配置
 * - request(url, method, body) - HTTP 请求
 * - parseJson(s) / parseJsonSafely(s) - JSON 解析
 * - base64Encode(str) / base64Decode(str) - Base64 编解码
 * - ajax(url, options) - AJAX 请求
 */
object JsBridge {
    private const val TAG = "JsBridge"

    /**
     * 在 Rhino 引擎上注册所有全局函数
     */
    fun registerGlobalFunctions(engine: RhinoScriptEngine, jsExt: JsExtensions) {
        // getVariable
        engine.putFunction("getVariable", object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any?>): Any? {
                val key = args.getOrNull(0)?.toString() ?: return null
                return Context.javaToJS(jsExt.getVariable(key), scope)
            }
        })

        // setVariable
        engine.putFunction("setVariable", object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any?>): Any? {
                val key = args.getOrNull(0)?.toString() ?: return null
                val value = args.getOrNull(1)
                // 如果 value 是 NativeObject/NativeArray，转为 Java Map/List
                val javaValue = rhinoToJava(value)
                jsExt.setVariable(key, javaValue)
                return null
            }
        })

        // BaseUrl
        engine.putFunction("BaseUrl", object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any?>): Any? {
                return jsExt.BaseUrl()
            }
        })

        // checkEnv
        engine.putFunction("checkEnv", object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any?>): Any? {
                return jsExt.checkEnv()
            }
        })

        // getCloudSettings
        engine.putFunction("getCloudSettings", object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any?>): Any? {
                val force = args.getOrNull(0).let {
                    when (it) {
                        is Boolean -> it
                        is Number -> it.toInt() != 0
                        else -> false
                    }
                }
                val result = jsExt.getCloudSettings(force)
                return Context.javaToJS(result, scope)
            }
        })

        // request
        engine.putFunction("request", object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any?>): Any? {
                val url = args.getOrNull(0)?.toString() ?: return ""
                val method = args.getOrNull(1)?.toString() ?: "GET"
                val body = args.getOrNull(2)
                val javaBody = rhinoToJava(body)
                return jsExt.request(url, method, javaBody)
            }
        })

        // ajax
        engine.putFunction("ajax", object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any?>): Any? {
                val url = args.getOrNull(0)?.toString() ?: return ""
                val options = args.getOrNull(1)?.toString()
                return if (options != null) jsExt.ajax(url, options) else jsExt.ajax(url)
            }
        })

        // base64Encode
        engine.putFunction("base64Encode", object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any?>): Any? {
                val str = args.getOrNull(0)?.toString() ?: return ""
                return jsExt.base64Encode(str)
            }
        })

        // base64Decode
        engine.putFunction("base64Decode", object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any?>): Any? {
                val str = args.getOrNull(0)?.toString() ?: return ""
                return jsExt.base64Decode(str)
            }
        })

        // hexDecodeToString
        engine.putFunction("hexDecodeToString", object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any?>): Any? {
                val str = args.getOrNull(0)?.toString() ?: return ""
                return jsExt.hexDecodeToString(str)
            }
        })

        // parseJson
        engine.putFunction("parseJson", object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any?>): Any? {
                val str = args.getOrNull(0)?.toString() ?: return null
                val result = jsExt.parseJson(str)
                return Context.javaToJS(result, scope)
            }
        })

        // parseJsonSafely - 安全版 parseJson，解析失败返回空对象
        engine.putFunction("parseJsonSafely", object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any?>): Any? {
                val str = args.getOrNull(0)?.toString() ?: return null
                val result = jsExt.parseJson(str)
                return Context.javaToJS(result ?: emptyMap<String, Any>(), scope)
            }
        })

        // get
        engine.putFunction("get", object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any?>): Any? {
                val url = args.getOrNull(0)?.toString() ?: return ""
                return jsExt.httpGet(url)
            }
        })

        // post
        engine.putFunction("post", object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any?>): Any? {
                val url = args.getOrNull(0)?.toString() ?: return ""
                val body = args.getOrNull(1)?.toString() ?: "{}"
                return jsExt.post(url, body)
            }
        })

        // getCookie
        engine.putFunction("getCookie", object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any?>): Any? {
                val domain = args.getOrNull(0)?.toString() ?: return ""
                val key = args.getOrNull(1)?.toString() ?: return ""
                return jsExt.getCookie(domain, key)
            }
        })

        // setCookie
        engine.putFunction("setCookie", object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any?>): Any? {
                val domain = args.getOrNull(0)?.toString() ?: return null
                val cookie = args.getOrNull(1)?.toString() ?: return null
                jsExt.setCookie(domain, cookie)
                return null
            }
        })

        // toast
        engine.putFunction("toast", object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any?>): Any? {
                jsExt.toast(args.getOrNull(0)?.toString() ?: "")
                return null
            }
        })

        // longToast
        engine.putFunction("longToast", object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any?>): Any? {
                jsExt.longToast(args.getOrNull(0)?.toString() ?: "")
                return null
            }
        })

        // log
        engine.putFunction("log", object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any?>): Any? {
                jsExt.log(args.getOrNull(0)?.toString() ?: "")
                return null
            }
        })

        // getSourceUrl
        engine.putFunction("getSourceUrl", object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any?>): Any? {
                return jsExt.getSourceUrl()
            }
        })

        // startBrowser
        engine.putFunction("startBrowser", object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any?>): Any? {
                val url = args.getOrNull(0)?.toString() ?: return ""
                val title = args.getOrNull(1)?.toString() ?: ""
                return jsExt.startBrowser(url, title)
            }
        })

        // stringToHex
        engine.putFunction("stringToHex", object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable, args: Array<out Any?>): Any? {
                val str = args.getOrNull(0)?.toString() ?: return ""
                return jsExt.stringToHex(str)
            }
        })

        Log.i(TAG, "全局函数注册完成")
    }

    /**
     * 将 Rhino 对象转为 Java 对象
     * NativeObject -> Map, NativeArray -> List, 其他 -> 原样
     */
    fun rhinoToJava(value: Any?): Any? {
        return when (value) {
            null -> null
            is org.mozilla.javascript.NativeObject -> {
                val map = LinkedHashMap<String, Any?>()
                for (key in value.ids) {
                    val k = key.toString()
                    map[k] = rhinoToJava(value.get(k, value))
                }
                map
            }
            is org.mozilla.javascript.NativeArray -> {
                val list = mutableListOf<Any?>()
                for (i in 0 until value.length.toInt()) {
                    list.add(rhinoToJava(value.get(i, value)))
                }
                list
            }
            is org.mozilla.javascript.Undefined -> null
            is org.mozilla.javascript.ConsString -> value.toString()
            is org.mozilla.javascript.Wrapper -> rhinoToJava(value.unwrap())
            else -> value
        }
    }
}

