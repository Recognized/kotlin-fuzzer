package com.github.recognized.metrics

import com.github.recognized.CompileTarget
import com.github.recognized.compile.SimpleMessageCollector
import com.github.recognized.mutation.firstNotNull
import com.github.recognized.runtime.TempFile
import com.github.recognized.runtime.disposing
import com.github.recognized.runtime.logger
import org.jetbrains.kotlin.cli.common.CLICompiler
import org.jetbrains.kotlin.cli.common.CommonCompilerPerformanceManager
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import org.jetbrains.kotlin.konan.file.File
import org.jetbrains.kotlin.utils.addToStdlib.cast
import kotlin.reflect.full.declaredMembers
import kotlin.reflect.jvm.isAccessible

interface FitnessFunction {
    fun score(code: String, statReporter: (String) -> Unit): Score
}

data class Score(
    val analyze: Int,
    val generate: Int,
    val compiled: Boolean
)

private val log = logger("Metrics")

class CompileTimeFitnessFunction<T : CommonCompilerArguments>(
    private val compiler: CLICompiler<T>, private val arguments: () -> T
) : FitnessFunction {
    private val messageCollector = SimpleMessageCollector()

    override fun score(code: String, statReporter: (String) -> Unit): Score {
        val exitCode = doCompile(code)
        log.trace { "exit code: $exitCode" }
        reportErrors(statReporter)
        return makeScore(exitCode)
    }

    private fun makeScore(exitCode: ExitCode): Score {
        val analyze = getAnalyzeTime() ?: error("Analyze time not found")
        val generate = getGenerateTime() ?: error("Generate time not found")

        return Score(
            analyze,
            generate,
            exitCode == ExitCode.OK && !messageCollector.hasErrors()
        )
    }

    private fun reportErrors(statReporter: (String) -> Unit) {
        messageCollector.errors().forEach {
            statReporter(it)
        }
    }

    private fun doCompile(code: String): ExitCode {
        disposing {
            messageCollector.clear()
            compiler.clear()
            val args = arguments()
            compiler.parseArguments(arrayOf("${TempFile(code, it).dir.toAbsolutePath()}"), args)
            val exitCode = compiler.exec(messageCollector, Services.EMPTY, args)
            reportOthers()
            return exitCode
        }
    }

    private fun reportOthers() {
        messageCollector.others().forEach {
            log.trace { it }
        }
    }

    private fun getAnalyzeTime(): Int? {
        return perf().find(ANALYZE_MS)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun getGenerateTime(): Int? {
        return perf().find(GENERATE_MS)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun perf(): List<String> = messageCollector.others().filter { it.startsWith("PERF") }

    companion object {
        val ANALYZE_MS = "ANALYZE: .*?in ([0-9]+) ms".toRegex()
        val GENERATE_MS = "GENERATE: .*?in ([0-9]+) ms".toRegex()
    }
}

fun Iterable<String>.find(regex: Regex): MatchResult? {
    return firstNotNull {
        regex.find(it)
    }
}

fun CommonCompilerArguments.addSource(path: String) {
    when (this) {
        is K2JVMCompilerArguments -> {
            classpath = listOfNotNull(path, classpath).joinToString(separator = File.pathSeparator)
        }
        else -> error("Cannot configure sources for ${this::class.java.simpleName}")
    }
}


private fun CLICompiler<*>.clear() {
    this::class.declaredMembers
        .first { "performance" in it.name }
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
        CompileTarget.Jvm -> {
            CompileTimeFitnessFunction(
                K2JVMCompiler()
            ) producer@{
                K2JVMCompilerArguments().apply {
                    reportPerf = true
                    this.kotlinHome = kotlinHome
                    this.destination = "outDir"
                    version = true
                }
            }
        }
        else -> error("Unsupported compile target $target")
    }
}