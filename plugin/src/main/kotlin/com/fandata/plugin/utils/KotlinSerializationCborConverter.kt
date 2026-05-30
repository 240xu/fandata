@file:Suppress("OPT_IN_USAGE")
package com.fandata.plugin.utils
import cxhttp.converter.CxHttpConverter;import cxhttp.response.CxHttpResult;import cxhttp.response.Response
import kotlinx.serialization.cbor.Cbor;import kotlinx.serialization.serializer;import java.lang.reflect.Type
@Suppress("UNCHECKED_CAST")
class KotlinSerializationCborConverter(private val cbor:Cbor=Cbor):CxHttpConverter{
    override val contentType="KotlinSerializationJson"
    override fun <T> convert(body:Response.Body,tType:Class<T>):T=cbor.decodeFromByteArray(cbor.serializersModule.serializer(tType),body.bytes())as T
    override fun <T,RESULT:CxHttpResult<T>> convertResult(body:Response.Body,resultType:Class<RESULT>,tType:Type):RESULT=cbor.decodeFromByteArray(cbor.serializersModule.serializer(resultType),body.bytes())as RESULT
    override fun <T,RESULT:CxHttpResult<List<T>>> convertResultList(body:Response.Body,resultType:Class<RESULT>,tType:Type):RESULT=cbor.decodeFromByteArray(cbor.serializersModule.serializer(resultType),body.bytes())as RESULT
    override fun <T> convert(value:T,tType:Class<out T>):ByteArray=cbor.encodeToByteArray(cbor.serializersModule.serializer(tType),value as Any)
}