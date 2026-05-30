package com.fandata.plugin.login

import android.content.Context
import android.webkit.CookieManager

/**
 * Cookie 瀛樺偍 - 绉绘鑷?Legado CookieStore
 */
object CookieStore {
    /** 可共享的 Cookie 映射（注入到引擎 HttpHelper） */
    val cookieStore = mutableMapOf<String, String>()
    private lateinit var prefs: android.content.SharedPreferences
    fun init(context: Context) { prefs = context.getSharedPreferences("fandata_cookies", Context.MODE_PRIVATE) }
    fun setCookie(domain: String, cookie: String?) {
        cookie?.let { cookieStore[domain] = it } ?: cookieStore.remove(domain)
        prefs.edit().putString(domain, cookie ?: "").apply()
        try { val cm = CookieManager.getInstance(); cookie?.split(";")?.forEach { cm.setCookie("https://$domain", it.trim()) } } catch (_: Exception) {}
    }
    fun getCookie(domain: String): String = prefs.getString(domain, "") ?: ""
    fun replaceCookie(domain: String, newCookie: String) {
        val old = getCookie(domain)
        if (old.isBlank()) { setCookie(domain, newCookie); return }
        val merged = cookieToMap(old).apply { putAll(cookieToMap(newCookie)) }
        setCookie(domain, mapToCookie(merged))
    }
    fun getCookieByUrl(url: String): String = getCookie(extractDomain(url))
    fun setCookieByUrl(url: String, cookie: String?) { setCookie(extractDomain(url), cookie) }
    fun removeCookie(domain: String) { prefs.edit().remove(domain).apply() }
    fun cookieToMap(cookie: String): MutableMap<String, String> {
        val m = mutableMapOf<String, String>(); if (cookie.isBlank()) return m
        cookie.split(";").forEach { val p = it.split("=", limit = 2); if (p.size == 2) m[p[0].trim()] = p[1].trim() }; return m
    }
    fun mapToCookie(m: Map<String, String>): String = m.entries.joinToString("; ") { "${it.key}=${it.value}" }
    private fun extractDomain(url: String): String = try { java.net.URI(url).host ?: url } catch (_: Exception) { url }
}
