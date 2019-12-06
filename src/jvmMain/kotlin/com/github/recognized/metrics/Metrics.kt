package com.github.recognized.metrics

import com.github.recognized.CompileTarget
import com.github.recognized.compile.SimpleMessageCollector
import com.github.recognized.mutation.firstNotNull
import com.github.recognized.runtime.logger
import com.github.recognized.service.Metrics
import org.jetbrains.kotlin.cli.common.CLICompiler
import org.jetbrains.kotlin.cli.common.CommonCompilerPerformanceManager
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.config.Services
import org.jetbrains.kotlin.konan.file.File
import org.jetbrains.kotlin.utils.addToStdlib.cast
import kotlin.reflect.full.declaredMembers
import kotlin.reflect.jvm.isAccessible

interface FitnessFunction {
    fun score(code: String): Score?
}

data class Score(
    val jitTime: Int,
    val javaClassFind: Int?,
    val compiled: Boolean
)

private val log = logger()

class CompileTimeFitnessFunction<T : CommonCompilerArguments>(
    private val compiler: CLICompiler<T>, private val arguments: T
) : FitnessFunction {
    private val messageCollector = SimpleMessageCollector()
    private val jitTime = "JIT time is ([0-9]+) ms".toRegex()
    private val javaClassFind = "Java class performed ([0-9]+) times".toRegex()
    private var lastJitTime = 0

    override fun score(code: String): Score? {
        messageCollector.clear()
        compiler.clear()
        val exitCode = compiler.exec(messageCollector, Services.EMPTY, arguments)
        log.info { "exit code: $exitCode" }
        messageCollector.errors().forEach {
            log.error { it }
        }
        val perf = messageCollector.others().filter {
            it.startsWith("PERF")
        }
        val jitTimeMs = extractNumber(jitTime, perf) ?: run {
            log.error { "Cannot find Jit time perf log" }
            return null
        }
        lastJitTime = jitTimeMs
        val javaClassFind = extractNumber(javaClassFind, perf)
        return Score(jitTimeMs, javaClassFind, exitCode == ExitCode.OK)
    }

    private fun extractNumber(regex: Regex, lines: List<String>): Int? {
        return lines.firstNotNull {
            regex.find(it)?.groupValues?.firstOrNull()?.toIntOrNull()
        }
    }
}

private fun CLICompiler<*>.clear() {
    this::class.declaredMembers
        .first { it.name.contains("performance") }
        .also {
            it.isAccessible = true
        }.call(this)
        .cast<CommonCompilerPerformanceManager>().let { manager ->
            CommonCompilerPerformanceManager::class.declaredMembers
                .first { it.name == "measurements" }
                .also { it.isAccessible = true }
                .call(manager).cast<MutableList<*>>()
                .clear()
        }
}

fun getCompileTimeFitnessFunction(target: CompileTarget, kotlinHome: String, classpath: List<String>): FitnessFunction {
    return when (target) {
        CompileTarget.Jvm -> CompileTimeFitnessFunction(
            K2JVMCompiler(),
            K2JVMCompilerArguments().apply {
                reportPerf = true
                this.kotlinHome = kotlinHome
                version = true
            }
        )
        else -> error("Unsupported compile target $target")
    }
}