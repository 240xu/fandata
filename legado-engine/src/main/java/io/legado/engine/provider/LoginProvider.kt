package io.legado.engine.provider
interface LoginProvider { fun openLogin(url:String,sourceUrl:String); fun getCookies(domain:String):String; fun setCookie(domain:String,cookie:String); fun isLoggedIn(domain:String):Boolean; fun getVerificationCode(imageUrl:String):String? }
