@file:JvmName("RPCGen")

package com.github.recognized.rpc

import com.github.recognized.service.Fuzzer
import java.io.BufferedWriter
import java.io.Closeable
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.full.createType
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.isSubtypeOf

fun CodeWriter.parameter(param: KParameter) {
    val inner = {
        line {
            "parameter(\"${param.name}\", ${stringify(param)})"
        }
    }
    if (param.type.isMarkedNullable) {
        block("if (${param.name} != null)") {
            inner()
        }
    } else {
        inner()
    }
}

fun stringify(param: KParameter): String {
    return when {
        param.type.isList() -> "stringify(${param.type.arguments.first().type}.serializer().list, ${param.name})"
        else -> "stringify(${param.type}.serializer(), ${param.name})"
    }
}

fun CodeWriter.generateRPCClientExtensions(service: KClass<*>) {
    val name = service.simpleName!!.substringAfterLast(".")
    line { "val HttpClient.$name get() = ${name}Proxy(this)" }
    block("class ${name}Proxy(val client: HttpClient) : ${service.qualifiedName!!}") {
        service.declaredFunctions.forEach { method ->
            val args = method.parameters.drop(1).joinToString(separator = ", ") { "${it.name}: ${it.type}" }
            block("override suspend fun ${method.name}($args): ${method.returnType}") {
                block("""val result___ = client.get<String>(server + "/api/$name")""") {
                    method.parameters.drop(1).forEach {
                        parameter(it)
                    }
                }
                if (method.returnType != Unit::class.createType()) {
                    when {
                        method.returnType.isList() -> line {
                            "return parse(${method.returnType.arguments.first().type!!}.serializer().list, result___)"
                        }
                        else -> line {
                            "return parse(${method.returnType}.serializer(), result___)"
                        }
                    }
                }
            }
        }
    }
}

private fun KType.isList() = arguments.isNotEmpty() && isSubtypeOf(List::class.createType(arguments))

fun generate(classes: List<KClass<*>>) {
    val genPath = Paths.get("src", "jsMain", "kotlin", "com/github/recognized/rpc")
    genPath.toFile().mkdirs()
    CodeWriter(Files.newBufferedWriter(genPath.resolve("Client.kt"))).use {
        it.apply {
            line { "import io.ktor.client.HttpClient" }
            line { "import io.ktor.client.request.get" }
            line { "import io.ktor.client.request.parameter" }
            line { "import com.github.recognized.parse" }
            line { "import com.github.recognized.stringify" }
            line { "import kotlinx.serialization.serializer" }
            for (clazz in classes) {
                generateRPCClientExtensions(clazz)
            }
        }
    }
}

fun main() {
    generate(
        listOf(
            Fuzzer::class
        )
    )
}

class CodeWriter(private val writer: BufferedWriter) : Closeable by writer {
    private var depth = 0

    fun indent(): String = (0 until depth).joinToString(separator = "") { "    " }

    fun line(fn: () -> String) {
        writer.write(indent() + fn() + "\n")
    }

    fun block(before: String, inside: CodeWriter.() -> Unit) {
        line { "$before {" }
        depth++
        inside()
        depth--
        line { "}" }
    }
}