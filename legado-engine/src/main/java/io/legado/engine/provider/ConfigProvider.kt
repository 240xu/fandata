package io.legado.engine.provider
interface ConfigProvider { fun getBoolean(key:String,default:Boolean=false):Boolean; fun setBoolean(key:String,value:Boolean); fun getString(key:String,default:String=""):String; fun setString(key:String,value:String); fun getInt(key:String,default:Int=0):Int; fun setInt(key:String,value:Int)
companion object Keys { const val KEY_PARAGRAPH_REVIEW="paragraph_review"; const val KEY_TEXT_REPLACE="	ext_replace"; const val KEY_SEARCH_MODE="search_mode"; const val KEY_EXPLORE_SOURCE="explore_source" } }
