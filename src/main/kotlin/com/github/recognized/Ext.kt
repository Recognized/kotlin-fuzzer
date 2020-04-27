package com.github.recognized

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

interface RPCService

val mapper = jacksonObjectMapper().registerKotlinModule()

fun <T> stringify(any: T): String {
    return mapper.writeValueAsString(any)
}

inline fun <reified T> parse(any: String): T {
    return when {
        T::class == List::class -> mapper.readValue(any, object : TypeReference<T>() {})
        else -> mapper.readValue(any, T::class.java)
    }
}