package io.legado.engine

import io.legado.engine.provider.CacheProvider
import io.legado.engine.provider.ConfigProvider
import io.legado.engine.provider.LoginProvider

/**
 * 引擎上下文 - 持有外部注入的 Provider 引用
 * 由 LNR 插件在初始化时设置
 */
object EngineContext {
    var configProvider: ConfigProvider? = null
    var loginProvider: LoginProvider? = null
    var cacheProvider: CacheProvider? = null

    fun init(config: ConfigProvider?, login: LoginProvider?, cache: CacheProvider?) {
        configProvider = config
        loginProvider = login
        cacheProvider = cache
    }

    /** 检查功能开关 */
    fun isFeatureEnabled(key: String, default: Boolean = false): Boolean {
        return configProvider?.getBoolean(key, default) ?: default
    }
}
