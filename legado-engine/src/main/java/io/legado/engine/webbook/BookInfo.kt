package io.legado.engine.webbook
import io.legado.engine.analyze.AnalyzeRule
import io.legado.engine.entity.*
object BookInfo {
    fun analyzeBookInfo(bookSource:BookSource,ruleData:RuleData,book:Book,body:String?,baseUrl:String){
        body?:return;val rule=bookSource.getBookInfoRule()
        val ar=AnalyzeRule(ruleData,bookSource);ar.setContent(body).setBaseUrl(baseUrl)
        try{rule.init?.let{if(it.isNotBlank())ar.evalJS(it,body)}
            book.name=ar.getString(rule.name).ifBlank{book.name};book.author=ar.getString(rule.author).ifBlank{book.author}
            book.coverUrl=ar.getString(rule.coverUrl).ifBlank{book.coverUrl};book.intro=ar.getString(rule.intro).ifBlank{book.intro}
            book.kind=ar.getStringList(rule.kind)?.joinToString(",")?:book.kind
            book.latestChapterTitle=ar.getString(rule.lastChapter).ifBlank{book.latestChapterTitle}
            book.wordCount=ar.getString(rule.wordCount).ifBlank{book.wordCount}
            val tu=ar.getString(rule.tocUrl);if(tu.isNotBlank())book.tocUrl=tu
        }catch(_:Exception){}
    }
}