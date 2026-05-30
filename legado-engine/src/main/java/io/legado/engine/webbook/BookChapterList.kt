package io.legado.engine.webbook
import io.legado.engine.analyze.AnalyzeRule
import io.legado.engine.entity.*
object BookChapterList {
    fun analyzeChapterList(bookSource:BookSource,ruleData:RuleData,book:Book,body:String?,baseUrl:String):ArrayList<BookChapter>{
        body?:throw Exception("目录内容为空");val chapterList=ArrayList<BookChapter>();val rule=bookSource.getTocRule()
        val ar=AnalyzeRule(ruleData,bookSource);ar.setContent(body).setBaseUrl(baseUrl)
        var rl=rule.chapterList?:"";var reverse=false
        if(rl.startsWith("-")){reverse=true;rl=rl.substring(1)};if(rl.startsWith("+")){rl=rl.substring(1)}
        val cols=ar.getElements(rl)
        for((i,item) in cols.withIndex()){
            ar.setContent(item).setBaseUrl(baseUrl)
            val ch=BookChapter(index=i,baseUrl=baseUrl,bookUrl=book.bookUrl)
            ch.title=try{ar.getString(rule.chapterName)}catch(_:Exception){""};if(ch.title.isBlank())continue
            ch.url=try{ar.getString(rule.chapterUrl,isUrl=true)}catch(_:Exception){""};if(ch.url.isBlank())continue
            if(!ch.url.startsWith("http"))ch.url=try{java.net.URL(java.net.URL(baseUrl),ch.url).toString()}catch(_:Exception){ch.url}
            ch.isVip=try{ar.getString(rule.isVip).toBoolean()}catch(_:Exception){false}
            ch.isPay=try{ar.getString(rule.isPay).toBoolean()}catch(_:Exception){false}
            chapterList.add(ch)
        }
        if(reverse)chapterList.reverse();chapterList.forEachIndexed{i,c->c.index=i};return chapterList
    }
}