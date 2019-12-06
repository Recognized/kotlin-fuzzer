package com.github.recognized

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration

interface RPCService

fun <T> stringify(serializer: SerializationStrategy<T>, any: T): String {
    return Json(JsonConfiguration.Stable.copy(prettyPrint = true)).stringify(serializer, any)
}

fun <T> parse(serializer: DeserializationStrategy<T>, any: String): T {
    return Json(JsonConfiguration.Stable).parse(serializer, any)
}