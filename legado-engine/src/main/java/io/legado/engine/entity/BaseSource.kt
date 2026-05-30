package io.legado.engine.entity

interface BaseSource {
    var concurrentRate: String?
    var loginUrl: String?
    var loginUi: String?
    var header: String?
    var enabledCookieJar: Boolean?
    var jsLib: String?
    fun getTag(): String
    fun getKey(): String
    fun getLoginJs(): String? {
        val loginJs = loginUrl
        return when {
            loginJs == null -> null
            loginJs.startsWith("@js:") -> loginJs.substring(4)
            loginJs.startsWith("<js>") -> loginJs.substring(4, loginJs.lastIndexOf("<"))
            else -> loginJs
        }
    }
    fun getHeaderMap(hasLogin: Boolean = true): Map<String, String>
    fun getLoginInfo(): String?
    fun putLoginInfo(info: String)
    fun removeLoginInfo()
    fun getVariable(): String
    fun putVariable(variable: String?)
}
