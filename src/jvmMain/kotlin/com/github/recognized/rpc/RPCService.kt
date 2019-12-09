package com.github.recognized.rpc

import com.github.recognized.RPCService
import com.github.recognized.parse
import com.github.recognized.runtime.logger
import com.github.recognized.service.Metrics
import com.github.recognized.service.Snippet
import com.github.recognized.service.SortOrder
import com.github.recognized.service.Statistics
import com.github.recognized.stringify
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.route
import kotlinx.serialization.*
import kotlin.collections.set
import kotlin.reflect.KClass
import kotlin.reflect.full.*
import kotlin.reflect.jvm.isAccessible

private val log = logger("RPCService")

@Suppress("UNCHECKED_CAST")
fun <T : RPCService> Route.serve(serviceClass: KClass<T>) {
    val serviceRoute = serviceClass.java.simpleName.substringAfterLast(".")
    log.info { "Service route $serviceRoute" }
    route(serviceRoute) {
        val impl = this::class.java.classLoader.loadClass(serviceClass.qualifiedName!! + "Impl").kotlin
        val instance = impl.createInstance()
        log.info { "Functions: ${serviceClass.declaredFunctions.map { it.name }}" }
        serviceClass.declaredFunctions.filter { it.isSuspend }.map { function ->
            log.info { "Function route ${function.name}" }
            get(function.name) {
                val args = mutableListOf<Any>()
                args.add(instance)
                function.parameters.drop(1).mapNotNullTo(args) { param ->
                    param.name?.let {
                        val p = call.request.queryParameters[it]
                        if (p != null) {
                            parse(SerializationRegistry.serializer<Any>(param.type.toString()), p)
                        } else {
                            null
                        }
                    }
                }

                log.info { "Calling ${function.name}" }

                val result = function.callSuspend(*args.toTypedArray())
                if (result !is Unit && result != null) {
                    val serializer = if (function.returnType.arguments.isNotEmpty()) {
                        when {
                            function.returnType.isSubtypeOf(List::class.createType(function.returnType.arguments)) -> SerializationRegistry.serializer<Any>(
                                function.returnType.arguments.first().type.toString()
                            ).list
                            else -> throw SerializationException("Method must return either List<R> or Set<R>, but it returns ${function.returnType}")
                        }
                    } else {
                        SerializationRegistry.serializer<Any>(function.returnType.toString())
                    }
                    call.respond(HttpStatusCode.OK, stringify(serializer as KSerializer<Any>, result))
                } else {
                    call.respond(HttpStatusCode.OK, "")
                }
            }
        }
    }
}

object SerializationRegistry {
    val serializers: MutableMap<String, DeserializationStrategy<*>> = mutableMapOf()

    private fun register(clazz: KClass<*>, serializer: KSerializer<*>) {
        val classname = clazz.qualifiedName!!
        assert(serializers[classname] == null)
        serializers[classname] = serializer
    }

    init {
        register(Metrics::class, Metrics.serializer())
        register(Snippet::class, Snippet.serializer())
        register(Statistics::class, Statistics.serializer())
        register(SortOrder::class, SortOrder.serializer())
        register(Int::class, Int.serializer())
        register(Double::class, Double.serializer())
        register(String::class, String.serializer())
        register(Long::class, Long.serializer())
    }

    @Suppress("unchecked_cast")
    inline fun <reified T> serializer(classname: String): KSerializer<T> {
        return serializers.getValue(classname) as KSerializer<T>
    }
}