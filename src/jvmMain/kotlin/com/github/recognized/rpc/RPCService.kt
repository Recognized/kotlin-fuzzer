package com.github.recognized.rpc

import com.github.recognized.RPCService
import com.github.recognized.parse
import com.github.recognized.stringify
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.route
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.list
import kotlin.collections.set
import kotlin.reflect.KClass
import kotlin.reflect.full.*
import kotlin.reflect.jvm.isAccessible

@Suppress("UNCHECKED_CAST")
fun <T : RPCService> Route.serve(serviceClass: KClass<T>) {
    route(serviceClass::class.java.simpleName.substringAfterLast(".")) {
        val impl = this::class.java.classLoader.loadClass(serviceClass.qualifiedName!! + "Impl").kotlin
        val instance = impl.createInstance()
        serviceClass.declaredMemberFunctions.filter { it.isAccessible && it.isSuspend }.map { function ->
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

                val result = function.callSuspend(*args.toTypedArray())
                if (result !is Unit && result != null) {
                    val serializer = if (function.returnType.arguments.isNotEmpty()) {
                        when {
                            function.returnType.isSubtypeOf(List::class.createType(function.returnType.arguments)) -> SerializationRegistry.serializer<Any>(
                                function.returnType.toString()
                            ).list
                            else -> throw SerializationException("Method must return either List<R> or Set<R>, but it returns ${function.returnType}")
                        }
                    } else {
                        SerializationRegistry.serializer<Any>(function.returnType.toString())
                    }
                    call.respond(stringify(serializer as KSerializer<Any>, result))
                } else {
                    call.respond(HttpStatusCode.OK, "")
                }
            }
        }
    }
}

object SerializationRegistry {
    val serializers: MutableMap<String, DeserializationStrategy<*>> = mutableMapOf()

    private fun register(classname: String, serializer: KSerializer<*>) {
        assert(serializers[classname] == null)
        serializers[classname] = serializer
    }

    init {
    }

    @Suppress("unchecked_cast")
    inline fun <reified T> serializer(classname: String): KSerializer<T> {
        return serializers[classname]!! as KSerializer<T>
    }
}