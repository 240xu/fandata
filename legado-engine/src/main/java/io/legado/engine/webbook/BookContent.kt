package io.legado.engine.webbook
import io.legado.engine.analyze.AnalyzeRule
import io.legado.engine.entity.*
object BookContent {
    fun analyzeContent(bookSource:BookSource,book:Book,chapter:BookChapter,body:String?,baseUrl:String):String{
        body?:throw Exception("正文内容为空");val rule=bookSource.getContentRule()
        val ar=AnalyzeRule(null,bookSource);ar.setContent(body).setBaseUrl(baseUrl)
        rule.webJs?.let{if(it.isNotBlank())ar.evalJS(it,body)}
        val contentRule=rule.content?:throw Exception("正文规则为空")
        val cl=ar.getStringList(contentRule);val sb=StringBuilder()
        for(item in cl){if(item.isNotBlank())sb.appendLine(item.trim())}
        var result=sb.toString().trim()
        rule.replaceRegex?.let{if(it.isNotBlank()){try{val p=it.split("##",limit=2);if(p.size==2)result=result.replace(Regex(p[0]),p[1])else result=result.replace(Regex(it),"")}catch(_:Exception){}}}
        return result
    }
}