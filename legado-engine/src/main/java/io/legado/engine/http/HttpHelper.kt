package io.legado.engine.http

import okhttp3.CookieJar
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class HttpHelper {
    val jsonType = "application/json; charset=utf-8".toMediaType()
    val formType = "application/x-www-form-urlencoded".toMediaType()
    val cookieStore = mutableMapOf<String, String>()
    /** 外部 Cookie 存储（由插件注入，与宿主共享登录态） */
    var externalCookieStore: MutableMap<String, String>? = null

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<okhttp3.Cookie>) {
                val domain = url.host
                val cookieStr = cookies.joinToString("; ") { "${it.name}=${it.value}" }
                cookieStore[domain] = cookieStr
                externalCookieStore?.set(domain, cookieStr)
            }
            override fun loadForRequest(url: okhttp3.HttpUrl): List<okhttp3.Cookie> {
                val domain = url.host
                val cookieStr = externalCookieStore?.get(domain) ?: cookieStore[domain] ?: return emptyList()
                return cookieStr.split("; ").mapNotNull { part ->
                    val kv = part.split("=", limit = 2)
                    if (kv.size == 2) okhttp3.Cookie.Builder()
                        .domain(domain).path("/").name(kv[0]).value(kv[1]).build()
                    else null
                }
            }
        })
        .build()

    fun get(url: String, headers: Map<String, String> = emptyMap(), cookie: String? = null): StrResponse {
        val builder = Request.Builder().url(url).get()
        for (entry in headers) { builder.addHeader(entry.key, entry.value) }
        if (cookie != null) builder.addHeader("Cookie", cookie)
        builder.addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
        val response = client.newCall(builder.build()).execute()
        return StrResponse(response, response.body?.string())
    }

    fun post(url: String, body: String = "{}", headers: Map<String, String> = emptyMap(), cookie: String? = null): StrResponse {
        val builder = Request.Builder().url(url).post(body.toRequestBody(jsonType))
        for (entry in headers) { builder.addHeader(entry.key, entry.value) }
        if (cookie != null) builder.addHeader("Cookie", cookie)
        builder.addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
        val response = client.newCall(builder.build()).execute()
        return StrResponse(response, response.body?.string())
    }

    fun postForm(url: String, fields: Map<String, String>, headers: Map<String, String> = emptyMap()): StrResponse {
        val formBody = fields.entries.joinToString("&") { "${it.key}=${java.net.URLEncoder.encode(it.value, "UTF-8")}" }
        val builder = Request.Builder().url(url).post(formBody.toRequestBody(formType))
        for (entry in headers) { builder.addHeader(entry.key, entry.value) }
        builder.addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
        val response = client.newCall(builder.build()).execute()
        return StrResponse(response, response.body?.string())
    }
}

class StrResponse(val raw: okhttp3.Response, val body: String?) {
    val url: String get() = raw.request.url.toString()
    fun code(): Int = raw.code
    fun isSuccessful(): Boolean = raw.isSuccessful
    fun headers() = raw.headers
}
