package io.legado.engine.entity
import com.google.gson.Gson
class RuleData : RuleDataInterface {
    override val variableMap by lazy { hashMapOf<String, String>() }
    override fun putBigVariable(key: String, value: String?) { if(value==null)variableMap.remove(key) else variableMap[key]=value }
    override fun getBigVariable(key: String): String? = null
    fun getVariable(): String? = if(variableMap.isEmpty()) null else Gson().toJson(variableMap)
}
interface RuleDataInterface {
    val variableMap: HashMap<String, String>
    fun putVariable(key: String, value: String?): Boolean {
        val exists = variableMap.contains(key)
        if(value==null){variableMap.remove(key);putBigVariable(key,null);return exists}
        if(value.length<10000){putBigVariable(key,null);variableMap[key]=value;return true}
        variableMap.remove(key);putBigVariable(key,value);return exists
    }
    fun putBigVariable(key: String, value: String?)
    fun getVariable(key: String): String = variableMap[key] ?: getBigVariable(key) ?: ""
    fun getBigVariable(key: String): String?
}