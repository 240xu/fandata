package com.fandata.plugin.settings

import io.legado.engine.provider.ConfigProvider
import io.nightfish.lightnovelreader.api.userdata.UserDataRepositoryApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class PluginConfigProvider(private val repo: UserDataRepositoryApi) : ConfigProvider {
    override fun getBoolean(key: String, default: Boolean): Boolean = 
        runBlocking { repo.booleanUserData(key).getFlowWithDefault(default).first() }
    override fun setBoolean(key: String, value: Boolean) { repo.booleanUserData(key).set(value) }
    override fun getString(key: String, default: String): String = 
        runBlocking { repo.stringUserData(key).getFlowWithDefault(default).first() }
    override fun setString(key: String, value: String) { repo.stringUserData(key).set(value) }
    override fun getInt(key: String, default: Int): Int = 
        runBlocking { repo.intUserData(key).getFlowWithDefault(default).first() }
    override fun setInt(key: String, value: Int) { repo.intUserData(key).set(value) }
}
